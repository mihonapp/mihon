"""Exercise installed extension packages over the real host IPC; never writes user data."""
import argparse
import json
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import zipfile
import concurrent.futures

parser = argparse.ArgumentParser()
parser.add_argument('--exe', default=str(Path(os.environ['LOCALAPPDATA']) / 'MihonW/MihonW.exe'))
parser.add_argument('--package', default='')
parser.add_argument('--images', action='store_true')
parser.add_argument('--query', default='')
parser.add_argument('--output', default=str(Path(tempfile.gettempdir()) / 'mihon-source-probe.json'))
args = parser.parse_args()
root = Path(os.environ['APPDATA']) / 'MihonW/extensions'
results = []
with tempfile.TemporaryDirectory(prefix='mihon-source-probe-', ignore_cleanup_errors=True) as work:
    err = open(Path(work) / 'stderr.log', 'w')
    proc = subprocess.Popen([args.exe, '--extension-host', '--stdio'], stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE, stderr=err, cwd=work)
    executor = concurrent.futures.ThreadPoolExecutor(1)
    def receive():
        prefix = proc.stdout.read(4)
        if len(prefix) != 4:
            raise RuntimeError('Host closed stdout')
        count = struct.unpack('>I', prefix)[0]
        return json.loads(proc.stdout.read(count))
    def call(command, payload):
        msg = {'type': 'mihon.extension.ipc.IpcRequest', 'requestId': 1,
               'command': command, 'payloadJson': json.dumps(payload)}
        data = json.dumps(msg).encode()
        proc.stdin.write(struct.pack('>I', len(data)) + data)
        proc.stdin.flush()
        res = executor.submit(receive).result(timeout=150)
        if not res.get('success'):
            raise RuntimeError(res.get('error', str(res)))
        return json.loads(res['payloadJson'])
    try:
        for package in root.rglob('*.mext'):
            if args.package not in package.name:
                continue
            item = {'package': package.stem}
            results.append(item)
            try:
                with zipfile.ZipFile(package) as z:
                    manifest = json.loads(z.read('manifest.json'))
                item['manifestSources'] = manifest['sources']
                sources = call('load_extension', {'packagePath': str(package), 'workingDir': str(Path(work) / package.stem)})
                item['runtimeSources'] = sources
                print(json.dumps({'package': package.stem, 'manifestIds': [s['id'] for s in manifest['sources']], 'runtime': sources}), flush=True)
                selected = next((s for s in sources if s.get('lang') in ('en', 'zh', 'all')), sources[0])
                sid = selected['id']
                item['selectedSource'] = sid
                popular = call('search_manga', {'sourceId': sid, 'page': 1, 'query': args.query}) if args.query else call('get_popular', {'sourceId': sid, 'page': 1})
                item['popularCount'] = len(popular['mangas'])
                manga = popular['mangas'][0]
                item['mangaUrl'] = manga['url']
                manga = call('get_manga_details', {'sourceId': sid, 'mangaJson': json.dumps(manga)})
                item['detailsOk'] = True
                chapters = call('get_chapter_list', {'sourceId': sid, 'mangaJson': json.dumps(manga)})
                item['chapterCount'] = len(chapters)
                if chapters:
                    chapter = next((c for c in chapters if '\U0001f512' not in c['name']), chapters[-1])
                    item['chapter'] = chapter
                    pages = call('get_page_list', {'sourceId': sid, 'chapterJson': json.dumps(chapter)})
                    item['pageCount'] = len(pages)
                    # Signed image URLs and request headers may contain credentials.
                    item['firstPageIndex'] = pages[0]['index'] if pages else None
                    if args.images and pages:
                        image = call('get_image', {'sourceId': sid, 'page': pages[0]})
                        image_file = Path(work) / 'page-images' / image['fileName']
                        data = image_file.read_bytes()
                        item['imageBytes'] = len(data)
                        item['imageSignature'] = data[:12].hex()
                        image_file.unlink()
            except Exception as e:
                item['error'] = str(e) or type(e).__name__
            print(json.dumps(item), flush=True)
            Path(args.output).write_text(json.dumps(results, indent=2), encoding='utf-8')
    finally:
        proc.kill()
        proc.wait()
        executor.shutdown(wait=False)
        err.close()
        print((Path(work) / 'stderr.log').read_text(errors='replace')[-5000:], flush=True)

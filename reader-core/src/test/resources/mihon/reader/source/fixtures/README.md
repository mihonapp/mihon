# Archive fixtures

These tiny archives were generated locally for Mihon W's reader security tests. The four format/encryption fixtures contain a file named `page.png` with the UTF-8 text `Mihon W Task 3 benign archive fixture page.`. The decoder-memory fixture contains a 140 MiB sparse all-zero file named `large-zero.png`, which compresses to a small archive. No third-party content is embedded. The generated archives may be redistributed with this test suite.

| Fixture | Generator and options | SHA-256 |
| --- | --- | --- |
| `valid-rar4.rar` | RAR 6.21 x64, `a -ma4 -ep1` | `CCBAC45F0AFBC1BF543B59CEFB22FD22C1F6AF243721813FB4FFAF1A0CD4B693` |
| `encrypted-rar5.rar` | RAR 6.21 x64, `a -ma5 -hpreader -ep1` | `BF688F62B9B6607240816A04529153D8BE782A7CCA1ACE4827BC74F9D5EFEEEE` |
| `encrypted.zip` | 7-Zip 26.01 x64, `a -tzip -preader -mem=AES256` | `AB5F4EB9606597CA7BC1D52C73D805ED070F69FD272D38BED4B1094875EF54A0` |
| `encrypted.7z` | 7-Zip 26.01 x64, `a -t7z -preader -mhe=on` | `DA304478045FDB13A09F9EAED95E5E2D93478D15F9E9AF56F03E53DA68160B26` |
| `malicious-large-dictionary.7z` | 7-Zip 26.01 x64, `a -t7z -m0=LZMA2:d=28 -mx=1`; resulting method reports a 192 MiB dictionary | `527215B4C105BC6DEC4E52E50CF4BAE67DF91E0ADFD7B77CBBF708E3220A3A70` |

Tests read these committed bytes directly and do not invoke RAR, 7-Zip, or the network.

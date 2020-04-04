package exh.eh

class GalleryNotUpdatedException(val network: Boolean, cause: Throwable) : RuntimeException(cause)

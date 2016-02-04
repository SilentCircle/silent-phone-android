prefix=@prefix@
exec_prefix=@exec_prefix@
libdir=@libdir@
includedir=@includedir@

Name: libzrtpcpp
Description: GNU ZRTP core library
Version: @VERSION@
Requires: @CRYPTOBACKEND@
Libs:  -L${libdir} -l@zrtplibName@
Cflags: -I${includedir} -I${includedir}/libzrtpcpp



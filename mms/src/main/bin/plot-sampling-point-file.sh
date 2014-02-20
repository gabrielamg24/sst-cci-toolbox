#! /bin/sh

. $MMS_INST/mymms
. $MMS_HOME/bin/mms-env.sh

java \
    -Dmms.home="$MMS_HOME" \
    -Xmx1024M $MMS_OPTIONS \
    -Djava.io.tmpdir=$TMPDIR \
    -classpath "$MMS_HOME/lib/*" \
    org.esa.cci.sst.tools.PlotSamplingPointFileTool "$@"
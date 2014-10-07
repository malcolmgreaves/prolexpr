#/bin/sh

PART_NUM=$1
FINAME="s3://cmuwcohen0/data/kbp/text/lucene_doc_dump/part-"
echo "Downloading: $FINAME$PART_NUM*"
echo "s3cmd get $FINAME$PART_NUM*"
time s3cmd get $FINAME$PART_NUM*

time for FI in `ls part-$PART_NUM*`;
do
echo "decompressing :: gzip -d $FI"
gzip -d $FI
done

echo "running RTextProcess"
echo "java -Xmx16G -server -cp schnell.jar schnell.process.text.concurrent.RTextProcess aws_RTextProcess.conf output part-$PART_NUM*"
time java -Xmx16G -server -cp schnell.jar schnell.process.text.concurrent.RTextProcess aws_RTextProcess.conf output part-$PART_NUM*

echo "compressing results in output"
for FI in `ls output`;
do
echo "compressing $FI"
gzip output/$FI
done

POST="s3://cmuwcohen0/data/kbp/text/lucene_doc_dump/processed"
echo "posting results to $POST/$NAME"
echo "s3cmd -r put output/* $POST/$NAME/"
time s3cmd put output/* $POST/$NAME/

echo "DONE"

#/bin/sh

echo "Getting s3config from malcolm's machine (need pw.)"
scp malcolm@128.2.210.171:~/.s3cfg .

echo "INSTALLING: java"
sudo apt-add-repository -y ppa:webupd8team/java
sudo apt-get update
sudo apt-get upgrade -y
echo debconf shared/accepted-oracle-license-v1-1 select true | sudo debconf-set-selections
echo debconf shared/accepted-oracle-license-v1-1 seen true | sudo debconf-set-selections
sudo apt-get install -y oracle-java7-set-default
java -version

echo "INSTALLING: s3cmd"
sudo apt-get install -y s3cmd

echo "done"

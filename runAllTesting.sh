#!/bin/bash

DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

cd $DIR

#RUN test first by building fake release THEN building fake project THEN building the fake project to make sure it works
#./gradlew clean build release -x javadoc
./gradlew clean build release -PexcludeSelenium=true -x javadoc
#./gradlew -Dorg.gradle.parallel=false -Dorg.gradle.configureondemand=false build -PexcludeSelenium=true -PexcludeH2Spec=true

test_result=$?
if [ $test_result -eq 0 ]
then
  echo "##################################"
  echo "Successfully BUILT FAKE MAVEN RELEASE(FOR TESTING) $?"
  echo "##################################"
else
  echo "##################################"
  echo "BUILDING FAKE MAVEN RELEASE(FOR TESTING) FAILED $test_result"
  echo "##################################"
  exit $test_result
fi

##############################################################
# Test upgrading legacy code to make sure we stay backwards compatible
#############################################################

if [ ! -d "../webpiecesexample-all" ]; then
   echo "legacy project is not on disk so git cloning now so we can test backwards compatibility"
   cd ..
   git clone https://github.com/deanhiller/webpiecesexample-all.git
   test_result=$?
   if [ $test_result -eq 0 ]
   then
       echo "##################################"
       echo "Successfully cloned legacy repo $?"
       echo "##################################"
   else
       echo "##################################"
       echo "FAILURE IN cloning legacy repo $test_result"
       echo "##################################"
       exit $test_result
   fi
   cd webpieces
else
   echo "Found legacy project already checked out=../webpiecesexample-all"
fi

cd ../webpiecesexample-all
git checkout master # just in case checkout the project to master

./gradlew clean build assembleDist
test_result=$?
if [ $test_result -eq 0 ]
then
  echo "##################################"
  echo "Successfully BUILT LEGACY PROJECT $?"
  echo "##################################"
else
  echo "##################################"
  echo "BUILDING LEGACY PROJECT FAILED $test_result"
  echo "##################################"
  exit $test_result
fi

#reset to webpieces directory
cd ../webpieces 

echo path=$PWD

##############################################################
# Test creation of project, build of new project and start of the server
#############################################################

cd webserver/output/webpiecesServerBuilder

echo path2=$PWD

./createProject.sh WebpiecesExample org.webpieces ..

echo createproject done
test_result=$?
if [ $test_result -eq 0 ]
then
  echo "##################################"
  echo "Successfully CREATED Project "
  echo "##################################"
else
  echo "##################################"
  echo "Example Project Creation Failed"
  echo "##################################"
  exit $test_result
fi

cd ../webpiecesexample-all
echo about to build assembleDist
./gradlew build assembleDist
test_result=$?
if [ $test_result -eq 0 ]
then
  echo "##################################"
  echo "Successfully BUILT EXAMPLE Project "
  echo "##################################"
else
  echo "##################################"
  echo "Example Project BUILD Failed"
  echo "##################################"
  exit $test_result
fi

cd webpiecesexample/output/distributions
unzip webpiecesexample.zip
test_result=$?
if [ $test_result -eq 0 ]
then
  echo "##################################"
  echo "Successfully Unzipped Production Server to `pwd`"
  echo "##################################"
else
  echo "##################################"
  echo "Unzip Production server FAILED"
  echo "##################################"
  exit $test_result
fi

#TODO: startup the server in background and run test to grep out success in log files
cd webpiecesexample
./bin/webpiecesexample &
server_pid=$!

echo "sleep 5 seconds"
sleep 5 
echo "Grepping log"

if grep -q "o.w.w.i.WebServerImpl     server started" logs/server.log; then
  echo "##################################"
  echo "11111 Server is located at `pwd`"
  echo "Server Startup Succeeded!!"
  echo "##################################"
else
  echo "##################################"
  echo "11111 Server Startup Failed to be done in 5 seconds"
  echo "Failed Startup.  Server is located at `pwd`"
  echo "##################################"
  kill -9 $server_pid
  exit 99
fi

#Downloading https page on server

#Test out a curl request to localhost to make sure basic webpage is working
curl -kL https://localhost:8443/@backend/secure/sslsetup > downloadedhtml.txt

if grep -q "BACKEND Login" downloadedhtml.txt; then
  kill -9 $server_pid
  echo "##################################"
  echo "2222 Server is located at `pwd`"
  echo "Server Download Page Successful!!"
  echo "##################################"
else
  echo "##################################"
  echo "2222 Server Startup Failed to be done in 5 seconds"
  echo "Failed Download https page.  Server is located at `pwd`"
  echo "##################################"
  kill -9 $server_pid
  exit 99
fi

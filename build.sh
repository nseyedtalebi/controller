#cd ..
#cd dashboard
#./build.sh
#cd ..
#cd controller
cd ..
cd sysinfo
./build.sh
cd ..
cd controller
mvn clean package bundle:bundle -U
cp target/controller-1.0-SNAPSHOT.jar ../agent/src/main/resources/
cp target/controller-1.0-SNAPSHOT.jar ../felix-framework-5.6.10/bundle/

#mvn clean package bundle:bundle -U
#cp target/controller-1.0-SNAPSHOT.jar ../agent/src/main/resources/
#cp target/controller-1.0-SNAPSHOT.jar /Users/cody/IdeaProjects/felix-framework-5.6.10/bundle 

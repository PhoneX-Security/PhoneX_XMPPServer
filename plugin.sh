#!/bin/bash

#
# This scripts takes built plugin class (with Ant/netbeans) and updates 
# existing plugin JAR file (which has strict structure) with new 
# compiled classes to reflect updates to the plugin file. 
#
# Warning: JSP page is not updated!
#
# @Author: Ph4r05
#


JAVA_FILE="OpenfireUserservicePlugin.jar"

CG='\e[0;32m'
CR='\e[0;31m'
CN='\e[0m'

echo -e "$CG[+]$CN going to build plugin with Ant"
ant

# extract plugin-userservice from the plugin zip file
echo -e "$CG[+]$CN Extracting subJar file with original classes from the plugin"
cd plugin
unzip userservice.jar lib/plugin-userservice.jar
RT=$?
if (( $RT != 0 )); then 
    echo -e "$CR[!]$CN Error: plugin/userservice.jar does not exists or has invalid structure"
    exit 1
fi


cd ..

# delete old unzip directory
echo -e "$CG[+]$CN Deleting old unzip directory"
/bin/rm -rf dist/unzipped 2> /dev/null

# unzip builded JAR file - obtain classes.
echo -e "$CG[+]$CN Unzipping built JAR archive with new plugin classes"
cd dist
unzip $JAVA_FILE -d unzipped
RT=$?
if (( $RT != 0 )); then 
    echo -e "$CR[!]$CN Error: dist/$JAVA_FILE (new plugin JAR file) does not exists or has invalid structure"
    exit 2
fi

# change directory so zip utility adds correct path to the archive.
cd unzipped

# replace new files in the plugin
echo -e "$CG[+]$CN Going to update plugin classes in sub-jar"
zip -rv ../../plugin/lib/plugin-userservice.jar org/ net/
RT=$?
if (( $RT != 0 )); then 
    echo -e "$CR[!]$CN Error: Cannot update subJar with new class files!"
    exit 2
fi

# update original plugin file
echo -e "$CG[+]$CN Going to update plugin jar file with new classes"
cd ../../plugin/
zip -rv userservice.jar lib
RT=$?
if (( $RT != 0 )); then 
    echo -e "$CR[!]$CN Error: Cannot update subJar in plugin JAR file!"
    exit 2
fi

# update plugin.xml
echo -e "$CG[+]$CN Updating plugin.xml"
cd ../
zip -rv plugin/userservice.jar plugin.xml

# cleanup
echo -e "$CG[+]$CN Cleaning up"
/bin/rm -rf dist/unzipped 2> /dev/null
/bin/rm -rf plugin/lib 2> /dev/null


echo -e "$CG[=]$CN Done, you can upload plugin/userservice.jar to the OpenFire server"


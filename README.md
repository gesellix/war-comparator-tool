# War Comparator Tool

WarComparator Tool allows to compare two war files.

It compares each war file by recursively comparing each internal file. If the internal files are found to be same, then the tool compares the internal jars for the files inside the jar.
The tool stops search at first different file. 

The tool exit with return value 1 if files are different or 0 if wars are similar.

Steps to run:

The tool can be run from command line as below:

```shell script
./gradlew run --args "<newWar> <newWarExtractFolder> <oldWar> <oldWarExtractFolder>"
```

With

    newWar = New war file path,
    oldWar = New war file path,
    newWarExtractFolder = Temporary folder where new war files can be extracted for comparasion,
    oldWarExtractFolder = Temporary folder where old war files can be extracted for comparasion

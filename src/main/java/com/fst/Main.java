package com.fst;
import com.fst.utils.OSGetter;
import com.fst.xmlfields.dependencies.*;
import com.fst.xmlfields.differences.DifferencesField;
import com.fst.xmlfields.differences.ModifiedClassField;
import com.fst.xmlfields.differences.ModifiedClassesField;
import com.fst.xmlfields.differences.NewClassesField;
import com.thoughtworks.xstream.XStream;
import jdk.jshell.spi.ExecutionControl.NotImplementedException;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.*;

public class Main {

    private static String dependencyFinderHomePath = null;
    private static String initialProjectVersionDirectoryPath;
    private static String modifiedProjectVersionDirectoryPath;
    private static final List<String> tempXMLOutputFilenames = Arrays.asList(
            "dependency_extractor_tempfile.xml",
            "c2c_tempfile.xml",
            "jarjardiff_tempfile.xml"
    );
    private static ArrayList<String> tempXMLOutputPaths = new ArrayList<>();

    private static Set<String> modifiedAndNewClassInbounds = new HashSet<>();
    private static DependenciesField classDependencies;
    private static DifferencesField classDifferences;

    public static void main(String[] args) throws NotImplementedException {
        setProjectDirectoriesPaths(args);
        tryToGetDependencyFinderHomePath(args[2]);

        setTempXMLOutputPaths();

        setClassesDependencies();
        setClassesDifferences();
        deleteTempFiles();

        setClassesInbounds();

        var testCasesToExecute =
                modifiedAndNewClassInbounds.stream()
                .filter(modifiedClassInbound -> modifiedClassInbound.endsWith("Test"))
                .toList();

        System.out.println();
    }

    private static void setClassesInbounds() {
        getModifiedClassesInbounds();
        getNewClassesInbounds();
    }

    private static void getModifiedClassesInbounds() {
        if (classDifferences.modifiedClassesField != null && classDifferences.modifiedClassesField.classes != null) {
            classDifferences.modifiedClassesField.classes
                    .stream()
                    .map(modifiedClass -> modifiedClass.name)
                    .forEach(modifiedClassName -> {
                        modifiedAndNewClassInbounds.add(modifiedClassName);
                        getClassInboundsForClass(modifiedClassName);
                    });
        }
    }

    private static void getNewClassesInbounds() {
        if (classDifferences.newClassesField != null && classDifferences.newClassesField.names != null) {
            classDifferences.newClassesField.names.forEach(newClassName -> {
                modifiedAndNewClassInbounds.add(newClassName);
                getClassInboundsForClass(newClassName);
            });
        }
    }

    private static void setClassesDifferences() throws NotImplementedException {
        runJarJarDiff();
        getDifferencesFromXML(tempXMLOutputPaths.get(2));
    }

    private static void removeNotConfirmedClassDependencies() {
        if (classDependencies.packages != null) {
            classDependencies.packages.removeIf(packageField -> packageField.confirmed.equals("no"));
            classDependencies.packages.forEach(packageField -> {
                packageField.classes.removeIf(classField -> classField.confirmed.equals("no"));
                packageField.classes.forEach(classField -> {
                    if (classField.inbounds != null) classField.inbounds.removeIf(inboundField -> inboundField.confirmed.equals("no"));
                    if (classField.outbounds != null) classField.outbounds.removeIf(outboundField -> outboundField.confirmed.equals("no"));
                });
            });
        }
    }

    private static void runJarJarDiff() throws NotImplementedException {
        String JarJarDiffCommand;
        JarJarDiffCommand = getJarJarDiffCommand();
        execCmd(JarJarDiffCommand);
        System.out.println();
    }

    private static String getJarJarDiffCommand() throws NotImplementedException {
        String CLICommand;
        if (OSGetter.isWindows()) {
            CLICommand = "cmd.exe /c JarJarDiff -code -out " + tempXMLOutputPaths.get(2)
                    + " -old-label Initial -old " + initialProjectVersionDirectoryPath
                    + " -new-label Modified -new " + modifiedProjectVersionDirectoryPath;
        } else if (OSGetter.isUnix() || OSGetter.isMac()) {
            CLICommand = dependencyFinderHomePath + "/bin/JarJarDiff -code -out " + tempXMLOutputPaths.get(2)
                    + " -old-label Initial -old " + initialProjectVersionDirectoryPath
                    + " -new-label Modified -new " + modifiedProjectVersionDirectoryPath;
        }
        else {
            throw new NotImplementedException("Your Operational System is not supported yet");
        }
        return CLICommand;
    }

    private static void getDifferencesFromXML(String tempXMLOutputPath) {
        var xStream = new XStream();
        xStream.ignoreUnknownElements();
        xStream.allowTypesByWildcard(new String[] {
                "com.fst.**",
        });
        xStream.processAnnotations(DifferencesField.class);
        xStream.processAnnotations(ModifiedClassesField.class);
        xStream.processAnnotations(ModifiedClassField.class);
        xStream.processAnnotations(NewClassesField.class);

        var XMLFile = new File(tempXMLOutputPath);

        classDifferences = (DifferencesField) xStream.fromXML(XMLFile);
    }

    private static void getClassInboundsForClass(String className) {
        classDependencies.packages
                .stream()
                .flatMap(packageField -> packageField.classes.stream())
                .filter(classField -> classField.name.equals(className))
                .forEach(classField -> {
                    if (classField.inbounds != null) {
                        classField.inbounds.forEach(inboundField -> {
                            if (modifiedAndNewClassInbounds.contains(inboundField.text)) {
                                return;
                            }
                            modifiedAndNewClassInbounds.add(inboundField.text);
                            getClassInboundsForClass(inboundField.text);
                        });
                    }
        });
    }

    private static void setProjectDirectoriesPaths(String[] args) {
        initialProjectVersionDirectoryPath = args[0];
        modifiedProjectVersionDirectoryPath = args[1];
    }

    private static void tryToGetDependencyFinderHomePath(String arg) {
        if (OSGetter.isUnix() || OSGetter.isMac()){
            if (arg == null) {
                throw new NullPointerException("3nd program argument must be DependencyFinder's home absolute path");
            }
            else {
                dependencyFinderHomePath = arg;
            }
        }
    }

    private static void setClassesDependencies() throws NotImplementedException {
        runDependencyExtractor();
        runClassToClass();

        getDependenciesFromXML();
        removeNotConfirmedClassDependencies();
    }

    private static void getDependenciesFromXML() {
        var xStream = new XStream();
        xStream.ignoreUnknownElements();
        xStream.allowTypesByWildcard(new String[] {
                "com.fst.**",
        });
        xStream.processAnnotations(DependenciesField.class);
        xStream.processAnnotations(PackageField.class);
        xStream.processAnnotations(ClassField.class);
        xStream.processAnnotations(InboundField.class);
        xStream.processAnnotations(OutboundField.class);

        var XMLFile = new File(tempXMLOutputPaths.get(1));

        classDependencies = (DependenciesField) xStream.fromXML(XMLFile);
    }

    private static void runClassToClass() throws NotImplementedException {
        var classToClassCommand = getClassToClassCommand();
        execCmd(classToClassCommand);
    }

    private static void runDependencyExtractor() throws NotImplementedException {
        var dependencyExtractorCommand = getDependencyExtractorCommand();
        execCmd(dependencyExtractorCommand);
    }

    private static void setTempXMLOutputPaths() {
        String mainClassFolderPath = getMainClassFolderPath();

        tempXMLOutputPaths = new ArrayList<>();
        for (String tempXMLOutputFilename: tempXMLOutputFilenames) {
            tempXMLOutputPaths.add(Paths.get(mainClassFolderPath, tempXMLOutputFilename).toAbsolutePath().toString());
        }
    }

    private static String getMainClassFolderPath() {
        File mainClassFile = null;
        try {
            mainClassFile = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return mainClassFile.getParentFile().getPath();
    }

    private static String getDependencyExtractorCommand() throws NotImplementedException {
        String CLICommand;
        if (OSGetter.isWindows()) {
            CLICommand = "cmd.exe /c DependencyExtractor -xml -out " + tempXMLOutputPaths.get(0) + " " + modifiedProjectVersionDirectoryPath;
        } else if (OSGetter.isUnix() || OSGetter.isMac()) {
            CLICommand = dependencyFinderHomePath + "/bin/DependencyExtractor -xml -out " + tempXMLOutputPaths.get(0) + " " + modifiedProjectVersionDirectoryPath;
        }
        else {
            throw new NotImplementedException("Your Operational System is not supported yet");
        }
        return CLICommand;
    }

    private static String getClassToClassCommand() throws NotImplementedException {
        String CLICommand;
        if (OSGetter.isWindows()) {
            CLICommand = "cmd.exe /c c2c " + tempXMLOutputPaths.get(0) + " -xml -out " + tempXMLOutputPaths.get(1);
        } else if (OSGetter.isUnix() || OSGetter.isMac()) {
            CLICommand = dependencyFinderHomePath + "/bin/c2c " + tempXMLOutputPaths.get(0) + " -xml -out " + tempXMLOutputPaths.get(1);
        }
        else {
            throw new NotImplementedException("Your Operational System is not supported yet");
        }
        return CLICommand;
    }

    private static void deleteTempFiles() {
        for (String filePath : tempXMLOutputFilenames) {
            File tempFile = new File(filePath);
            tempFile.delete();
        }
    }


    public static void execCmd(String cmd) {
        System.out.println();
        System.out.println("Running CLI Command: " + cmd);
        String errorResult = null;
        try (
                InputStream inputStream = Runtime.getRuntime().exec(cmd).getErrorStream();
                Scanner s = new Scanner(inputStream).useDelimiter("\\A")
        ) {
            errorResult = s.hasNext() ? s.next() : null;
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (errorResult != null) {
            throw new RuntimeException(
                    "Error when executing the CLI command" + "\n"
                            + "Error: " + errorResult);
        }
    }
}

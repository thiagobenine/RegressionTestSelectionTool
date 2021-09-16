package com.fst;
import com.fst.utils.OSGetter;
import com.fst.xmlfields.*;
import com.thoughtworks.xstream.XStream;
import jdk.jshell.spi.ExecutionControl.NotImplementedException;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws NotImplementedException {
        var inputDirectoryPath =  args[0];
        System.out.println("Input directory path: " + inputDirectoryPath);

        var classDependencies = getClassDependencies(inputDirectoryPath);

        System.out.println();
    }

    private static DependenciesField getClassDependencies(String inputDirectoryPath) throws NotImplementedException {
        ArrayList<String> tempXMLOutputPaths;
        tempXMLOutputPaths = getTempXMLOutputPaths();

        runDependencyExtractor(inputDirectoryPath, tempXMLOutputPaths.get(0));
        runClassToClass(tempXMLOutputPaths);
        var dependencies = getDependenciesFromXML(tempXMLOutputPaths.get(1));
        deleteFiles(tempXMLOutputPaths);

        return dependencies;
    }

    private static DependenciesField getDependenciesFromXML(String tempXMLOutputPath) {
        var xStream = new XStream();
        xStream.allowTypesByWildcard(new String[] {
                "com.fst.**",
        });
        xStream.processAnnotations(DependenciesField.class);
        xStream.processAnnotations(PackageField.class);
        xStream.processAnnotations(ClassField.class);
        xStream.processAnnotations(InboundField.class);
        xStream.processAnnotations(OutboundField.class);

        var XMLFile = new File(tempXMLOutputPath);

        return (DependenciesField) xStream.fromXML(XMLFile);
    }

    private static void runClassToClass(ArrayList<String> tempXMLOutputPaths) throws NotImplementedException {
        String classToClassCommand;
        classToClassCommand = getClassToClassCommand(tempXMLOutputPaths.get(1), tempXMLOutputPaths.get(0));
        execCmd(classToClassCommand);
    }

    private static void runDependencyExtractor(String inputDirectoryPath, String tempXMLOutputPath) throws NotImplementedException {
        String dependencyExtractorCommand;
        dependencyExtractorCommand = getDependencyExtractorCommand(tempXMLOutputPath, inputDirectoryPath);
        execCmd(dependencyExtractorCommand);
    }

    private static ArrayList<String> getTempXMLOutputPaths() {
        String mainClassFolderPath = getMainClassFolderPath();
        var tempXMLOutputFilenames = Arrays.asList(
                "dependency_extractor_tempfile.xml",
                "c2c_tempfile.xml"
        );

        var tempXMLOutputPathsList = new ArrayList<String>();
        for (String tempXMLOutputFilename: tempXMLOutputFilenames) {
            tempXMLOutputPathsList.add(Paths.get(mainClassFolderPath, tempXMLOutputFilename).toAbsolutePath().toString());
        }

        return tempXMLOutputPathsList;
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

    private static String getDependencyExtractorCommand(String XMLOutputPath, String inputDirectoryPath) throws NotImplementedException {
        String CLICommand;
        if (OSGetter.isWindows()) {
            CLICommand = "cmd.exe /c DependencyExtractor -xml -out " + XMLOutputPath + " " + inputDirectoryPath;
        } else if (OSGetter.isUnix() || OSGetter.isMac()) {
            CLICommand = "DependencyExtractor -xml -out " + XMLOutputPath + " " + inputDirectoryPath;
        }
        else {
            throw new NotImplementedException("Your Operational System is not supported yet");
        }
        return CLICommand;
    }

    private static String getClassToClassCommand(String XMLOutputPath, String XMLInputPath) throws NotImplementedException {
        String CLICommand;
        if (OSGetter.isWindows()) {
            CLICommand = "cmd.exe /c c2c " + XMLInputPath + " -xml -out " + XMLOutputPath;
        } else if (OSGetter.isUnix() || OSGetter.isMac()) {
            CLICommand = "c2c " + XMLInputPath + " -xml -out " + XMLOutputPath;
        }
        else {
            throw new NotImplementedException("Your Operational System is not supported yet");
        }
        return CLICommand;
    }

    private static boolean isOutputInvalid(String XMLOutputPath) {
        File tempFile = new File(XMLOutputPath);
        boolean outputExists = tempFile.exists();
        if (outputExists) {
            System.out.println("Output file already exists");
            return true;
        } else {
            try {
                tempFile.createNewFile();
            } catch (IOException e) {
                System.out.println("Invalid path: " + XMLOutputPath);
                e.printStackTrace();
                return true;
            }
            tempFile.delete();
        }
        return false;
    }

    private static void deleteFiles(ArrayList<String> filePaths) {
        for (String filePath : filePaths) {
            File tempFile = new File(filePath);
            tempFile.delete();
        }
    }


    public static void execCmd(String cmd) {
        System.out.println();
        System.out.println("CLI Command: " + cmd);
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
            throw new RuntimeException("Error when executing the following CLI command: " + cmd);
        }
    }
}

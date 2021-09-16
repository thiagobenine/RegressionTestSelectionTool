package com.company;

import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;

public class Main {

    public static void main(String[] args)  {
        var inputDirectoryPath =  args[0];
        System.out.println("Input directory path: " + inputDirectoryPath);

        ArrayList<String> tempXMLOutputPaths;
        tempXMLOutputPaths = getTempXMLOutputPaths();
        if (tempXMLOutputPaths == null || tempXMLOutputPaths.isEmpty()) return; // maybe throw an exception instead of checking for null

        String dependencyExtractorCommand;
        dependencyExtractorCommand = getDependencyExtractorCommand(tempXMLOutputPaths.get(0), inputDirectoryPath);
        if (dependencyExtractorCommand == null) return; // maybe throw an exception instead of checking for null

        var dependencyExtractorOutputError = execCmd(dependencyExtractorCommand);

        if (dependencyExtractorOutputError == null) {
            System.out.println("DependencyExtractor ran without errors");

        } else {
            System.out.println("Error when executing DependencyExtractor");
            System.out.println("Output: \n" + dependencyExtractorOutputError);
            System.out.println("Exiting..");
            return;
        }

        String classToClassCommand;
        classToClassCommand = getClassToClassCommand(tempXMLOutputPaths.get(1), tempXMLOutputPaths.get(0));
        if (classToClassCommand == null) return; // maybe throw an exception instead of checking for null

        var classToClassOutputError = execCmd(classToClassCommand);

        if (classToClassOutputError == null) {
            System.out.println("c2c ran without errors");

        } else {
            System.out.println("Error when executing c2c");
            System.out.println("Output: \n" + classToClassOutputError);
        }

        deleteFiles(tempXMLOutputPaths);
    }

    private static ArrayList<String> getTempXMLOutputPaths(){
        var tempXMLOutputPathsList = new ArrayList<String>();
        if (OSGetter.isWindows()) {
            tempXMLOutputPathsList.add("C:\\TCC\\tempfile.xml");
            tempXMLOutputPathsList.add("C:\\TCC\\tempfile2.xml");
        } else if (OSGetter.isUnix() || OSGetter.isMac()) {
            tempXMLOutputPathsList.add("~/tempfile.xml");
            tempXMLOutputPathsList.add("~/tempfile2.xml");
        }
        else {
            System.out.println("Operational System not supported. Exiting.");
            return null;
        }
        return tempXMLOutputPathsList;
    }

    private static String getDependencyExtractorCommand(String XMLOutputPath, String inputDirectoryPath) {
        String CLICommand;
        if (OSGetter.isWindows()) {
            CLICommand = "cmd.exe /c DependencyExtractor -xml -out " + XMLOutputPath + " " + inputDirectoryPath;
        } else if (OSGetter.isUnix() || OSGetter.isMac()) {
            CLICommand = "DependencyExtractor -xml -out " + XMLOutputPath + " " + inputDirectoryPath;
        }
        else {
            System.out.println("Operational System not supported. Exiting.");
            return null;
        }
        return CLICommand;
    }

    private static String getClassToClassCommand(String XMLOutputPath, String XMLInputPath) {
        String CLICommand;
        if (OSGetter.isWindows()) {
            CLICommand = "cmd.exe /c c2c " + XMLInputPath + " -xml -out " + XMLOutputPath;
        } else if (OSGetter.isUnix() || OSGetter.isMac()) {
            CLICommand = "c2c " + XMLInputPath + " -xml -out " + XMLOutputPath;
        }
        else {
            System.out.println("Operational System not supported. Exiting.");
            return null;
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


    public static String execCmd(String cmd) {
        System.out.println();
        System.out.println("CLI Command: " + cmd);
        String result = null;
        try (
                InputStream inputStream = Runtime.getRuntime().exec(cmd).getErrorStream();
                Scanner s = new Scanner(inputStream).useDelimiter("\\A")
        ) {
            result = s.hasNext() ? s.next() : null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }
}

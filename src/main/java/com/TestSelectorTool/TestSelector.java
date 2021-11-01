package com.TestSelectorTool;

import com.TestSelectorTool.utils.OSGetter;
import com.TestSelectorTool.utils.SelectionTechniqueEnum;
import com.TestSelectorTool.xmlfields.dependencies.*;
import com.TestSelectorTool.xmlfields.differences.DifferencesField;
import com.TestSelectorTool.xmlfields.differences.ModifiedClassField;
import com.TestSelectorTool.xmlfields.differences.ModifiedClassesField;
import com.TestSelectorTool.xmlfields.differences.NewClassesField;
import com.google.common.collect.Lists;
import com.strobel.decompiler.Decompiler;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;
import com.thoughtworks.xstream.XStream;
import jdk.jshell.spi.ExecutionControl.NotImplementedException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TestSelector {
    private String dependencyFinderHomePath = null;
    private final String initialProjectVersionDirectoryPath;
    private final String modifiedProjectVersionDirectoryPath;
    private final List<String> tempXMLOutputFilenames = Arrays.asList(
            "dependency_extractor_for_modified_version_tempfile.xml",
            "c2c_for_modified_version_tempfile.xml",
            "jarjardiff_tempfile.xml",
            "dependency_extractor_for_initial_version_tempfile.xml",
            "c2c_for_initial_version_tempfile.xml"
    );
    private  ArrayList<String> tempXMLOutputPaths = new ArrayList<>();
    private final SelectionTechniqueEnum selectionTechnique;
    private final Set<String> modifiedAndNewClassInbounds = new HashSet<>();
    private  DependenciesField modifiedVersionClassDependencies;
    private  DifferencesField classDifferences;
    private DependenciesField initialVersionTestsClassDependencies;
    private final Set<String> selectedTestClasses = new HashSet<>();
    private final Set<String> newClassesFromNewPackages = new HashSet<>();

    public  void main(String[] args) {}

    public TestSelector(
            String initialProjectVersionDirectoryPath,
            String modifiedProjectVersionDirectoryPath,
            SelectionTechniqueEnum selectionTechnique
    ) {
        this.initialProjectVersionDirectoryPath = initialProjectVersionDirectoryPath;
        this.modifiedProjectVersionDirectoryPath = modifiedProjectVersionDirectoryPath;
        this.selectionTechnique = selectionTechnique;
    }

    public TestSelector(
            String initialProjectVersionDirectoryPath,
            String modifiedProjectVersionDirectoryPath,
            SelectionTechniqueEnum selectionTechnique,
            String dependencyFinderHomePath
    ) {
        this(initialProjectVersionDirectoryPath, modifiedProjectVersionDirectoryPath, selectionTechnique);
        this.dependencyFinderHomePath = dependencyFinderHomePath;
    }

    public List<String> getSelectedClasses() throws NotImplementedException {
        checkDependencyFinderHomePathRequirement();

        setTempXMLOutputPaths();

        setClassesDependenciesForModifiedVersion();
        setClassesDependenciesForTestsFromInitialVersion();
        setClassesDifferencesBetweenInitialAndModifiedVersion();

        if (selectionTechnique.equals(SelectionTechniqueEnum.CLASS_FIREWALL)) {
            setClassesInboundsFromDifferences(false);
        }
        else if (selectionTechnique.equals(SelectionTechniqueEnum.CHANGE_BASED)){
            setClassesInboundsFromDifferences(true);
        }

        getSelectedTestCasesUsingClassesInbounds();
        deleteTempFiles();

        return selectedTestClasses.stream().toList();
    }

    private void getSelectedTestCasesUsingClassesInbounds() {
        modifiedAndNewClassInbounds.forEach(modifiedAndNewClassInbound ->
                initialVersionTestsClassDependencies.packages.forEach(testPackageField ->
                        testPackageField.classes.forEach(testClassField -> {
                            if (testClassField.name.equals(modifiedAndNewClassInbound))
                                selectedTestClasses.add(modifiedAndNewClassInbound);
                        })));
    }

    private void setClassesInboundsFromDifferences(boolean stopAtFirstLevel) {
        getModifiedClassesInbounds(stopAtFirstLevel);
        getNewClassesInbounds(stopAtFirstLevel);
    }

    private void getModifiedClassesInbounds(boolean stopAtFirstLevel) {
        if (classDifferences.modifiedClassesField != null && classDifferences.modifiedClassesField.classes != null) {
            classDifferences.modifiedClassesField.classes
                    .stream()
                    .map(modifiedClass -> modifiedClass.name)
                    .forEach(modifiedClassName -> {
                        modifiedAndNewClassInbounds.add(modifiedClassName);
                        getClassInboundsForClass(modifiedClassName, stopAtFirstLevel);
                    });
        }
    }

    private void getNewClassesInbounds(boolean stopAtFirstLevel) {
        if (classDifferences.newClassesField != null && classDifferences.newClassesField.names != null) {
            classDifferences.newClassesField.names.forEach(newClassName -> {
                modifiedAndNewClassInbounds.add(newClassName);
                getClassInboundsForClass(newClassName, stopAtFirstLevel);
            });
        }
        if (!newClassesFromNewPackages.isEmpty()) {
            newClassesFromNewPackages.forEach(newClassName -> {
                modifiedAndNewClassInbounds.add(newClassName);
                getClassInboundsForClass(newClassName, stopAtFirstLevel);
            });
        }
    }

    private void setClassesDifferencesBetweenInitialAndModifiedVersion() throws NotImplementedException {
        runJarJarDiff();
        getDifferencesFromXML(tempXMLOutputPaths.get(2));

        analyzeClassFilesToGetNewClassesFromNewPackages();
    }

    private void analyzeClassFilesToGetNewClassesFromNewPackages() {
        if (classDifferences.newPackagesField != null && classDifferences.newPackagesField.names != null) {
            var newPackagesNames = new ArrayList<String>(classDifferences.newPackagesField.names);

            try {
                ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

                var paths = Files.walk(Paths.get(modifiedProjectVersionDirectoryPath))
                        .filter(path -> path.toString().endsWith(".class"))
                        .toList();

                var numberOfPaths = paths.size();
                var numberOfThreads = 8;

                var chunkSize = numberOfPaths / (numberOfThreads - 1);

                var subSetsPaths = Lists.partition(paths, chunkSize);
                System.out.println("Number of Subsets of chunkSize " + chunkSize + ": " + subSetsPaths.size());
                subSetsPaths
                        .forEach(listOfPaths -> {
                            executor.submit(() -> {
                                listOfPaths.forEach(path -> {
                                    System.out.println("Decompiling: " + path);
                                    final DecompilerSettings settings = DecompilerSettings.javaDefaults();
                                    var plainTextOutput = new PlainTextOutput();
                                    Decompiler.decompile(
                                            path.toString(),
                                            plainTextOutput,
                                            settings
                                    );

                                    var output = plainTextOutput.toString();
                                    String newClassName = path.getFileName().toString().replace(".class", "");

                                    for (String newPackageName : newPackagesNames) {
                                        var packageName = "package " + newPackageName;

                                        if (output.contains(packageName)) {
                                            String newClassFullName = packageName + "." + newClassName;
                                            newClassesFromNewPackages.add(newClassFullName);
                                            break;
                                        }
                                    }
                                });
                                executor.shutdown();
                                return null;
                            });
                        });
                executor.awaitTermination(20, TimeUnit.MINUTES);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void removeNotConfirmedClassDependenciesForModifiedVersion() {
        if (modifiedVersionClassDependencies.packages != null) {
            modifiedVersionClassDependencies.packages.removeIf(packageField -> packageField.confirmed.equals("no"));
            modifiedVersionClassDependencies.packages.forEach(packageField -> {
                packageField.classes.removeIf(classField -> classField.confirmed.equals("no"));
                packageField.classes.forEach(classField -> {
                    if (classField.inbounds != null) classField.inbounds.removeIf(inboundField -> inboundField.confirmed.equals("no"));
                    if (classField.outbounds != null) classField.outbounds.removeIf(outboundField -> outboundField.confirmed.equals("no"));
                });
            });
        }
    }

    private void removeNotConfirmedClassDependenciesForTestsFromInitialVersion() {
        if (initialVersionTestsClassDependencies.packages != null) {
            initialVersionTestsClassDependencies.packages.removeIf(packageField -> packageField.confirmed.equals("no"));
            initialVersionTestsClassDependencies.packages.forEach(packageField -> {
                packageField.classes.removeIf(classField -> classField.confirmed.equals("no"));
                packageField.classes.forEach(classField -> {
                    if (classField.inbounds != null) classField.inbounds.removeIf(inboundField -> inboundField.confirmed.equals("no"));
                    if (classField.outbounds != null) classField.outbounds.removeIf(outboundField -> outboundField.confirmed.equals("no"));
                });
            });
        }
    }

    private void runJarJarDiff() throws NotImplementedException {
        String JarJarDiffCommand;
        JarJarDiffCommand = getJarJarDiffCommand();
        execCmd(JarJarDiffCommand);
        System.out.println();
    }

    private String getJarJarDiffCommand() throws NotImplementedException {
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

    private void getDifferencesFromXML(String tempXMLOutputPath) {
        var xStream = new XStream();
        xStream.ignoreUnknownElements();
        xStream.allowTypesByWildcard(new String[] {
                "com.TestSelectorTool.**",
        });
        xStream.processAnnotations(DifferencesField.class);
        xStream.processAnnotations(ModifiedClassesField.class);
        xStream.processAnnotations(ModifiedClassField.class);
        xStream.processAnnotations(NewClassesField.class);

        var XMLFile = new File(tempXMLOutputPath);

        classDifferences = (DifferencesField) xStream.fromXML(XMLFile);
    }

    private void getClassInboundsForClass(String className, boolean stopAtFirstLevel) {
        modifiedVersionClassDependencies.packages
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

                            if (inboundField.text.contains("Test")) {
                                return;  // should not get dependencies for Tests Classes
                            }

                            if (!stopAtFirstLevel) {
                                getClassInboundsForClass(inboundField.text, false);
                            }
                        });
                    }
        });
    }

    private void checkDependencyFinderHomePathRequirement() {
        if (OSGetter.isUnix() || OSGetter.isMac()){
            if (dependencyFinderHomePath == null) {
                throw new NullPointerException("You need to instantiate TestSelector() " +
                        "with DependencyFinder's home absolute path");
            }
        }
    }

    private void setClassesDependenciesForTestsFromInitialVersion() throws NotImplementedException {
        runDependencyExtractorForInitialVersion();
        runClassToClassForInitialVersion();

        getDependenciesForTestsFromInitialVersionFromXML();
        removeNotConfirmedClassDependenciesForTestsFromInitialVersion();
    }


    private void getDependenciesForTestsFromInitialVersionFromXML() {
        var xStream = new XStream();
        xStream.ignoreUnknownElements();
        xStream.allowTypesByWildcard(new String[] {
                "com.TestSelectorTool.**",
        });
        xStream.processAnnotations(DependenciesField.class);
        xStream.processAnnotations(PackageField.class);
        xStream.processAnnotations(ClassField.class);
        xStream.processAnnotations(InboundField.class);
        xStream.processAnnotations(OutboundField.class);

        var XMLFile = new File(tempXMLOutputPaths.get(4));

        var initialVersionClassDependencies = (DependenciesField) xStream.fromXML(XMLFile);

        if (initialVersionClassDependencies.packages != null) {
            initialVersionClassDependencies.packages.forEach(packageField ->
                    // get only Test Classes
                    packageField.classes.removeIf(classField ->
                            !classField.name.contains("Test"))
            );
        }

        initialVersionTestsClassDependencies = initialVersionClassDependencies;
    }

    private void runClassToClassForInitialVersion() throws NotImplementedException {
        var classToClassCommand = getClassToClassCommandForInitialVersion();
        execCmd(classToClassCommand);
    }

    private void runDependencyExtractorForInitialVersion() throws NotImplementedException {
        var dependencyExtractorCommand = getDependencyExtractorCommandForInitialVersion();
        execCmd(dependencyExtractorCommand);
    }

    private void setClassesDependenciesForModifiedVersion() throws NotImplementedException {
        runDependencyExtractorForModifiedVersion();
        runClassToClassForModifiedVersion();

        getDependenciesForModifiedVersionFromXML();
        removeNotConfirmedClassDependenciesForModifiedVersion();
    }

    private void getDependenciesForModifiedVersionFromXML() {
        var xStream = new XStream();
        xStream.ignoreUnknownElements();
        xStream.allowTypesByWildcard(new String[] {
                "com.TestSelectorTool.**",
        });
        xStream.processAnnotations(DependenciesField.class);
        xStream.processAnnotations(PackageField.class);
        xStream.processAnnotations(ClassField.class);
        xStream.processAnnotations(InboundField.class);
        xStream.processAnnotations(OutboundField.class);

        var XMLFile = new File(tempXMLOutputPaths.get(1));

        modifiedVersionClassDependencies = (DependenciesField) xStream.fromXML(XMLFile);
    }

    private void runClassToClassForModifiedVersion() throws NotImplementedException {
        var classToClassCommand = getClassToClassCommandForModifiedVersion();
        execCmd(classToClassCommand);
    }

    private void runDependencyExtractorForModifiedVersion() throws NotImplementedException {
        var dependencyExtractorCommand = getDependencyExtractorCommandForModifiedVersion();
        execCmd(dependencyExtractorCommand);
    }

    private void setTempXMLOutputPaths() {
        String mainClassFolderPath = getMainClassFolderPath();

        tempXMLOutputPaths = new ArrayList<>();
        for (String tempXMLOutputFilename: tempXMLOutputFilenames) {
            tempXMLOutputPaths.add(Paths.get(mainClassFolderPath, tempXMLOutputFilename).toAbsolutePath().toString());
        }
    }

    private String getMainClassFolderPath() {
        File mainClassFile = null;
        try {
            mainClassFile = new File(TestSelector.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return mainClassFile.getParentFile().getPath();
    }

    private String getDependencyExtractorCommandForInitialVersion() throws NotImplementedException {
        String CLICommand;
        if (OSGetter.isWindows()) {
            CLICommand = "cmd.exe /c DependencyExtractor -xml -out " + tempXMLOutputPaths.get(3) + " " + initialProjectVersionDirectoryPath;
        } else if (OSGetter.isUnix() || OSGetter.isMac()) {
            CLICommand = dependencyFinderHomePath + "/bin/DependencyExtractor -xml -out " + tempXMLOutputPaths.get(3) + " " + initialProjectVersionDirectoryPath;
        }
        else {
            throw new NotImplementedException("Your Operational System is not supported yet");
        }
        return CLICommand;
    }

    private String getDependencyExtractorCommandForModifiedVersion() throws NotImplementedException {
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

    private String getClassToClassCommandForInitialVersion() throws NotImplementedException {
        String CLICommand;
        if (OSGetter.isWindows()) {
            CLICommand = "cmd.exe /c c2c " + tempXMLOutputPaths.get(3) + " -xml -out " + tempXMLOutputPaths.get(4);
        } else if (OSGetter.isUnix() || OSGetter.isMac()) {
            CLICommand = dependencyFinderHomePath + "/bin/c2c " + tempXMLOutputPaths.get(3) + " -xml -out " + tempXMLOutputPaths.get(4);
        }
        else {
            throw new NotImplementedException("Your Operational System is not supported yet");
        }
        return CLICommand;
    }

    private String getClassToClassCommandForModifiedVersion() throws NotImplementedException {
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

    private void deleteTempFiles() {
        for (String filePath : tempXMLOutputFilenames) {
            File tempFile = new File(filePath);
            tempFile.delete();
        }
    }


    public void execCmd(String cmd) {
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

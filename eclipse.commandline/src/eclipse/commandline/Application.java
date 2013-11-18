package eclipse.commandline;

import gr.uom.java.ast.Standalone;
import gr.uom.java.distance.ExtractClassCandidateGroup;
import gr.uom.java.distance.ExtractClassCandidateRefactoring;
import gr.uom.java.distance.MoveMethodCandidateRefactoring;
import gr.uom.java.jdeodorant.refactoring.manipulators.ASTSlice;
import gr.uom.java.jdeodorant.refactoring.manipulators.ASTSliceGroup;
import gr.uom.java.jdeodorant.refactoring.manipulators.TypeCheckElimination;
import gr.uom.java.jdeodorant.refactoring.manipulators.TypeCheckEliminationGroup;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.LibraryLocation;
import org.eclipse.jdt.ui.PreferenceConstants;

public class Application implements IApplication {

	@Override
	public Object start(IApplicationContext arg0) throws Exception {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();
		IPath rootPath = root.getLocation();
		File rootFile = rootPath.toFile();
		File[] contents = rootFile.listFiles();
		
		for(File file : contents) {
			if(file.isDirectory() && !file.getName().startsWith(".")) {
				String[] dirContents = file.list();
				List<String> dirContentsList = Arrays.asList(dirContents);
				if(dirContentsList.contains(".project")) {
					IProjectDescription description = ResourcesPlugin.getWorkspace().loadProjectDescription(new Path(file.getPath() + "/.project"));
					IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(description.getName());
					if(!project.exists()) {
						project.create(null);
					}
					else {
						project.refreshLocal(IResource.DEPTH_INFINITE, null);
					}
					if (!project.isOpen()) {
						project.open(null);
					}
					if(project.hasNature(JavaCore.NATURE_ID)) {
						IJavaProject jproject = JavaCore.create(project);
						if(!jproject.hasBuildState()) {
							project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
							System.out.println("Project " + project.getName() + " built");
						}
					}
				}
				else // if project doesn't have a valid eclipse project open RMA
				{
					//First create a simple project of type org.eclipse.core.resources.IProject:
					IProject project = root.getProject(file.getName());
					if(!project.exists()) {
						project.create(null);
					}
					else {
						project.refreshLocal(IResource.DEPTH_INFINITE, null);
					}
					if (!project.isOpen()) {
						project.open(null);
					}
					//Because we need a java project, we have to set the Java nature to the created project:
					IProjectDescription description = project.getDescription();
					description.setNatureIds(new String[] { JavaCore.NATURE_ID });
					project.setDescription(description, null);
					 
					/*However, it's not enough if we want to add Java source code to the project. We have to set the Java build path:
					(1) We first specify the output location of the compiler (the bin folder):*/
					IFolder binFolder = project.getFolder("bin");
					if(!binFolder.exists()) {
						binFolder.create(false, true, null);
					}
					//Now we can create our Java project
					IJavaProject javaProject = JavaCore.create(project);
					javaProject.setOutputLocation(binFolder.getFullPath(), null);
					/*
					(2) Define the class path entries. Class path entries define the roots of package fragments. Note that you might have to include the necessary plugin
					"org.eclipse.jdt.launching".
					List<IClasspathEntry> entries = new ArrayList<IClasspathEntry>();
					IVMInstall vmInstall = JavaRuntime.getDefaultVMInstall();
					LibraryLocation[] locations = JavaRuntime.getLibraryLocations(vmInstall);
					for (LibraryLocation element : locations) {
						entries.add(JavaCore.newLibraryEntry(element.getSystemLibraryPath(), null, null));
					}
					//add libs to project class path
					javaProject.setRawClasspath(entries.toArray(new IClasspathEntry[entries.size()]), null);
					*/
					//(3) We have not yet the source folder created:
					IFolder sourceFolder = project.getFolder("src");
					if(!sourceFolder.exists()) {
						sourceFolder.create(false, true, null);
					}
					//(4) Now the created source folder should be added to the class entries of the project, otherwise compilation will fail:
					IPackageFragmentRoot packageFragmentRoot = javaProject.getPackageFragmentRoot(sourceFolder);//change sourceFolder fort src/main/java
					
					String libFolderName = "lib";
					IFolder libFolder = project.getFolder(libFolderName);
					if(!libFolder.exists()) {
						libFolder.create(false, true, null);
					}
					IResource[] members = libFolder.members();
					List<IClasspathEntry> libEntries = new ArrayList<IClasspathEntry>();
					for(IResource resource : members) {
						if(resource.getFileExtension().equals("jar")) {
							IClasspathEntry libEntry = JavaCore.newLibraryEntry(resource.getFullPath(), null, null, false);
							libEntries.add(libEntry);
						}
					}
					IClasspathEntry[] defaultJRELibraryEntries = PreferenceConstants.getDefaultJRELibrary();
					IClasspathEntry[] libEntryArray = libEntries.toArray(new IClasspathEntry[libEntries.size()]);
					IClasspathEntry[] newEntries = new IClasspathEntry[defaultJRELibraryEntries.length + libEntryArray.length + 1];
					System.arraycopy(defaultJRELibraryEntries, 0, newEntries, 0, defaultJRELibraryEntries.length);
					System.arraycopy(libEntryArray, 0, newEntries, defaultJRELibraryEntries.length, libEntryArray.length);
					newEntries[newEntries.length-1] = JavaCore.newSourceEntry(packageFragmentRoot.getPath());
					javaProject.setRawClasspath(newEntries, null);
					
					if(!javaProject.hasBuildState()) {
						project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
						System.out.println("Project " + project.getName() + " built");
					}
				}
			}
		}

		workspace.save(true, null);
		IProject[] projects = root.getProjects();
		
		//RMA WRITING FILES
		
		BufferedWriter writer = null;
		
		try
		{
			
			for(IProject project : projects) {
				File SmellFile=new File(project.getName()+".txt");
				writer = new  BufferedWriter ( new FileWriter(SmellFile));
				long numberofSmells=0;
				long numberofFeatureEnvy=0;
				long numberofLongMethod=0;
				long numberofStateChecking=0;
				long numberofGodClass=0;
				if(project.isOpen()) {
					System.out.println("Running JDeodorant on project " + project.getName());
					System.out.println(SmellFile.getCanonicalPath());
					if(project.hasNature(JavaCore.NATURE_ID)) {
						IJavaProject jproject = JavaCore.create(project);
						List<MoveMethodCandidateRefactoring> moveMethodCandidateList = Standalone.getMoveMethodRefactoringOpportunities(jproject);
						System.out.println("Move Method Refactoring Opportunities:");
						numberofFeatureEnvy=moveMethodCandidateList.size();
						writer.write("Feature Envy "+numberofFeatureEnvy);
						writer.newLine();
						long contador=0;
						for(MoveMethodCandidateRefactoring candidate : moveMethodCandidateList) {
							//						System.out.println(candidate.getSourceEntity());
							
							System.out.println(candidate);
//							contador++;
							String[] mycandidate=candidate.toString().split("\\s+");
							writer.write(candidate.getSourceClass()+"|"+candidate.getMovedMethodName()+"|"+candidate.getTargetClass());
							writer.newLine();
						}

						//State checking
						Set<TypeCheckEliminationGroup> typeCheckEliminationGroupList = Standalone.getTypeCheckEliminationRefactoringOpportunities(jproject);
						System.out.println("Type Check Elimination Refactoring Opportunities:");
						writer.write("State Checking");
						writer.newLine();
						for(TypeCheckEliminationGroup group : typeCheckEliminationGroupList) {
							List<TypeCheckElimination> typeCheckEliminationList = group.getCandidates();
							for(TypeCheckElimination elimination : typeCheckEliminationList) {
								System.out.println(elimination);
								String[] myelimination=elimination.toString().split(":");
//								writer.write(myelimination[0]+"|"+myelimination[2]+
								writer.write(myelimination[0]+"|"+elimination.getAbstractMethodName()+
										"|"+elimination.getAbstractMethodName());
								writer.newLine();
							}
						}

						//long method
						Set<ASTSliceGroup> sliceGroupList = Standalone.getExtractMethodRefactoringOpportunities(jproject);
						System.out.println("Extract Method Refactoring Opportunities:");
						numberofLongMethod=sliceGroupList.size();
						writer.write("Number of long method: "+numberofLongMethod);
						//writer.write("LongMethod");
						writer.newLine();
						for(ASTSliceGroup group : sliceGroupList) {
							Set<ASTSlice> slices = group.getCandidates();
							
							String NotToRepeat;
							for(ASTSlice slice : slices) {
								System.out.println(slice);
								
								String[] mySlice=slice.toString().split("\\s+");
								
								writer.write(mySlice[0]+"|"+slice.getSourceMethodDeclaration().getName().toString()+"|"+slice.getLocalVariableCriterion().getName());
//								writer.write(slice+"|"+slice.getExtractedMethodName()+"|"+slice.getLocalVariableCriterion().toString());
								writer.newLine();
							}
						}

						//God Class
						Set<ExtractClassCandidateGroup> extractClassGroupList = Standalone.getExtractClassRefactoringOpportunities(jproject);
						System.out.println("Extract Class Refactoring Opportunities:");
						writer.write("GodClass");
						writer.newLine();
						for(ExtractClassCandidateGroup group : extractClassGroupList) {
							List<ExtractClassCandidateRefactoring> candidates = group.getCandidates();
							for(ExtractClassCandidateRefactoring candidate : candidates) {
								System.out.println(candidate);
								
								writer.write(candidate.getSourceEntity().lastIndexOf(".")+1);
//								writer.write(candidate.getSource());
								writer.newLine();
							}
						}
					}
				}
			}
		}
		finally
		{
			if (writer != null) try {writer.close();}
			catch(IOException ignore){}
		}
		
		return IApplication.EXIT_OK;
	}

	@Override
	public void stop() {

	}

}

package ult.lib1;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.jd.core.v1.ClassFileToJavaSourceDecompiler;
import org.jd.core.v1.api.loader.Loader;
import org.jd.core.v1.api.loader.LoaderException;
import org.jd.core.v1.api.printer.Printer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

class JDLoader implements Loader {

	String className = null;
	byte[] bytes = null;

	public JDLoader(String className, byte[] bytes)
	{
		this.className = className;
		this.bytes = bytes;
	}
    @Override
    public byte[] load(String internalName) throws LoaderException {
    	System.err.println("load(): " + internalName);
    	return this.bytes;
    }
    @Override
    public boolean canLoad(String internalName) {
        return Objects.equals(this.className, internalName);
    }
}

public class Main {

	public static List<String> getClassNamesFromJarFile(File givenFile) throws IOException {
	    List<String> classNames = new ArrayList<>();
	    try (JarFile jarFile = new JarFile(givenFile)) {
	        Enumeration<JarEntry> e = jarFile.entries();
	        while (e.hasMoreElements()) {
	            JarEntry jarEntry = e.nextElement();
	            if (jarEntry.getName().endsWith(".class") && !jarEntry.getName().contains("$")) {
	                String className = jarEntry.getName()
	                  .replace("/", ".")
	                  .replace(".class", "");
	                classNames.add(className);
	            }
	        }
	        return classNames;
	    }
	}

	public static byte[] getClassBytesFromJarFile(File givenFile, String className)
	{
	    byte[] bytes = null;
	    try (JarFile jarFile = new JarFile(givenFile)) {
	        Enumeration<JarEntry> e = jarFile.entries();
	        while (e.hasMoreElements()) {
	            JarEntry jarEntry = e.nextElement();
	            if (jarEntry.getName().endsWith(className.replace('.', '/') + ".class")) {
	            	byte[] buf = new byte[1024];
	            	int readsize = 0;
	            	InputStream is = jarFile.getInputStream(jarEntry);
	            	bytes = new byte[is.available()];
	            	/*
	            	while ((readsize = is.read(buf, 0, 1024)) != -1) {
	            	    // 読み込んだデータを処理
	            	    byte[] destination = new byte[bytes.length + readsize];
	            	    System.arraycopy(bytes, 0, destination, 0, bytes.length);
	            	    System.arraycopy(buf, 0, destination, bytes.length, readsize);
	            	    bytes = destination;
	            	}
	            	*/
	            	is.read(bytes);
	            	is.close();
	            	break;
	            }
	        }
	    } catch (IOException e1) {
	    	System.err.println(e1.getStackTrace());
	    } finally {
	        return bytes;
	    }
	}

	public static void investigate(File jarFile) throws Exception
	{
		List<String> classes = getClassNamesFromJarFile(jarFile);
		System.err.println(classes.size());
		System.err.println(classes);
		for(int i=0; i<classes.size(); i++)
		{
			String className = classes.get(i);
			System.err.println(className);
			byte[] classBytes = getClassBytesFromJarFile(jarFile, className);
			System.err.println(classBytes.length);
			Loader loader = new JDLoader(className, classBytes);
			Printer printer = new Printer() {
			    protected static final String TAB = "  ";
			    protected static final String NEWLINE = "\n";
			    protected int indentationCount = 0;
			    protected StringBuilder sb = new StringBuilder();
			    @Override public String toString() { return sb.toString(); }
			    @Override public void start(int maxLineNumber, int majorVersion, int minorVersion) {}
			    @Override public void end() {}
			    @Override public void printText(String text) { sb.append(text); }
			    @Override public void printNumericConstant(String constant) { sb.append(constant); }
			    @Override public void printStringConstant(String constant, String ownerInternalName) { sb.append(constant); }
			    @Override public void printKeyword(String keyword) { sb.append(keyword); }
			    @Override public void printDeclaration(int type, String internalTypeName, String name, String descriptor) { sb.append(name); }
			    //@Override public void printReference(int type, String internalTypeName, String name, String descriptor, String ownerInternalName) { sb.append(name); }
			    @Override public void printReference(int type, String internalTypeName, String name, String descriptor, String ownerInternalName) {
			    	switch(type)
			    	{
			    	case 1:
			    		/*
			    		if(name == "byte")
			    		{
			    			sb.append(name);
			    			return;
			    		}
			    		*/
			    		if(!internalTypeName.contains("/"))
			    		{
			    			sb.append(name);
			    			return;
			    		}
			    		sb.append(internalTypeName.replace('/', '.'));
			    		return;
			    		//break;
			    	}
			    	if(sb.toString().endsWith("this.")) ;
			    	else if(sb.toString().endsWith(".")) ;
			    	else if(ownerInternalName != null) name = ownerInternalName.replace('/', '.') + "." + name;
			    	sb.append(name);
		    	}
			    @Override public void indent() { this.indentationCount++; }
			    @Override public void unindent() { this.indentationCount--; }
			    @Override public void startLine(int lineNumber) { for (int i=0; i<indentationCount; i++) sb.append(TAB); }
			    @Override public void endLine() { sb.append(NEWLINE); }
			    @Override public void extraLine(int count) { while (count-- > 0) sb.append(NEWLINE); }
			    @Override public void startMarker(int type) {}
			    @Override public void endMarker(int type) {}
			};

			ClassFileToJavaSourceDecompiler decompiler = new ClassFileToJavaSourceDecompiler();

			decompiler.decompile(loader, printer, "JDMain");
			//decompiler.decompile(loader, printer, "D:\\repo\\priv3\\java\\src\\Main.class");
			//decompiler.decompile(loader, printer, "D:\\pleiades-2020-12-java-win-64bit-jre_20201222\\workspace\\jd-core-test-01\\build\\classes\\java\\main\\Main.class");

			String source = printer.toString();

			System.err.println(source);

            // Set up a minimal type solver that only looks at the classes used to run this sample.
	        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
	        combinedTypeSolver.add(new ReflectionTypeSolver());

	        // Configure JavaParser to use type resolution
	        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedTypeSolver);
	        StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);

			CompilationUnit cu = StaticJavaParser.parse(source);
			NodeList<ImportDeclaration> imports = cu.getImports();
			for(int i2=0; i2<imports.size(); i2++)
			{
				System.err.print(imports.get(i2));
			}
			System.err.println();
			NodeList<TypeDeclaration<?>> types = cu.getTypes();
			for(int i3=0; i3<types.size(); i3++)
			{
				TypeDeclaration<?> type = types.get(i3);
				System.err.println(type);
				List<Node> childNodes = type.getChildNodes();
				for(int i4=0; i4<childNodes.size(); i4++)
				{
					Node child = childNodes.get(i4);
					if(child instanceof FieldDeclaration)
					{
						FieldDeclaration fd = (FieldDeclaration)child;
						System.err.println(fd);
						System.err.println(fd.getVariables());
					}
				}
			}
		}

	}

	public static void main(String[] args) throws Exception {
		String path = "./build/libs/ult-lib1-1.0.0.jar";
		File jar = new File(path);
		investigate(jar);
	}
}

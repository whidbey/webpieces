package org.webpieces.compiler;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.webpieces.compiler.impl.CompilationException;


public class ModifyToErrorTest extends AbstractCompileTest {

	@Override
	protected String getPackageFilter() {
		return "org.webpieces.compiler.error";
	}
	
	@SuppressWarnings("rawtypes")
	@Test
	public void testSimpleChangeMethodNameAndRetVal() {
		log.info("loading class ErrorController");
		//DO NOT CALL Classname.getClass().getName() so that we don't pre-load it from the default classloader and
		//instead just tediously form the String ourselves...
		Class c = compiler.loadClass("org.webpieces.compiler.error.ErrorController");

		log.info("loaded");
		int retVal = invokeMethodReturnInt(c, "someMethod");
		
		Assert.assertEquals(5, retVal);
		
		cacheAndMoveFiles();
		
		try {
			compiler.loadClass("org.webpieces.compiler.error.ErrorController");
		} catch(CompilationException e) {
			List<String> source = e.getSource();
			Assert.assertTrue(e.getMessage().contains("The method noMethodExists() is undefined"));
			Assert.assertEquals(new Integer(9), e.getLineNumber());
			Assert.assertTrue(e.getSourceFile().endsWith("ErrorController.java"));
			//verify the source line 9 is at the 8th position in list of Strings
			Assert.assertTrue(source.get(8).contains("new ChildClassNoError(). noMethodExists()"));
		}
		
	}


}

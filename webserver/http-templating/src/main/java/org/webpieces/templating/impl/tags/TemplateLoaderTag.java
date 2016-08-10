package org.webpieces.templating.impl.tags;

import java.io.PrintWriter;
import java.util.Map;

import org.webpieces.templating.api.HtmlTag;
import org.webpieces.templating.api.ReverseUrlLookup;
import org.webpieces.templating.api.Template;
import org.webpieces.templating.api.TemplateService;
import org.webpieces.templating.api.TemplateUtil;
import org.webpieces.templating.impl.GroovyTemplateSuperclass;

import groovy.lang.Binding;
import groovy.lang.Closure;

public abstract class TemplateLoaderTag implements HtmlTag {

	protected TemplateService svc;
	
	@Override
	public void runTag(Map<Object, Object> tagArgs, Closure<?> body, PrintWriter out, GroovyTemplateSuperclass parentTemplate,
			String srcLocation) {
		Binding binding = parentTemplate.getBinding();
		@SuppressWarnings("unchecked")
		Map<String, Object> pageArgs = binding.getVariables();
		
		Map<String, Object> customTagArgs = convertTagArgs(tagArgs, pageArgs);
		
		ReverseUrlLookup lookup = parentTemplate.getUrlLookup();
		
		String filePath = getFilePath(parentTemplate, tagArgs, body, srcLocation);
		Template template = svc.loadTemplate(filePath);

		Map<Object, Object> templateProps = parentTemplate.getTemplateProperties();
		String s = svc.runTemplate(template, customTagArgs, templateProps, lookup);
		out.print(s);
	}
	
	protected abstract Map<String, Object> convertTagArgs(Map<Object, Object> tagArgs, Map<String, Object> pageArgs);

	protected String getFilePath(GroovyTemplateSuperclass callingTemplate, Map<Object, Object> args, Closure<?> body, String srcLocation) {
        Object name = args.get("_arg");
        if(name == null)
        	throw new IllegalArgumentException("#{"+getName()+"/}# tag must contain a template name like #{"+getName()+" '../template.html'/}#. "+srcLocation);
        else if(body != null)
        	throw new IllegalArgumentException("Only #{"+getName()+"/}# can be used.  You cannot do #{"+getName()+"}# #{/"+getName()+"} as the body is not used with this tag");
        
        String path = TemplateUtil.translateToProperFilePath(callingTemplate, name.toString());
		return path;
	}
	
	/**
	 * This is a bit nasty circular dependency but this tag is special and whether in dev or prod mode needs
	 * to re-use all the loadTemplate/runTemplate logic
	 * 
	 * TEmplateService -> HtmlTagLookup -> HtmlFileTag -> TemplateService
	 * 
	 * @param svc
	 */
	public void initialize(TemplateService svc) {
		this.svc = svc;
	}
}
package play.modules.pandoc;

import play.Play;
import play.classloading.enhancers.LocalvariablesNamesEnhancer;
import play.data.validation.Validation;
import play.exceptions.PlayException;
import play.exceptions.TemplateNotFoundException;
import play.exceptions.UnexpectedException;
import play.mvc.Http;
import play.mvc.Scope;

import java.io.OutputStream;
import java.util.List;

/**
 * @author: bjoerge
 * @created 6/24/11 8:52 PM
 */
public class Pandoc {

	public static void renderPandoc(String format, Object... args) {
		renderPandoc(PandocPlugin.Format.fromString(format), args);
	}

	public static void renderPandoc(PandocPlugin.Format format, Object... args) {
		renderPandoc(format, false, args);
	}

	public static void renderPandoc(PandocPlugin.Format format, boolean attachment, Object... args) {
		final Http.Request request = Http.Request.current();
		final String reqformat = request.format;

		String templateName = "";
		if (args.length > 0 && args[0] instanceof String && LocalvariablesNamesEnhancer.LocalVariablesNamesTracer.getAllLocalVariableNames(args[0]).isEmpty()) {
			templateName = args[0].toString();
		}
		else {
			templateName = request.action.replace(".", "/") + "." + (reqformat == null ? "html" : reqformat);
		}
        renderPandoc(format, attachment, templateName, args);
	}

    public static void renderPandoc(PandocPlugin.Format format, boolean attachment, String templateName, Object... args) {
        renderPandoc(format, attachment, templateName, null, args);
    }

	public static void renderPandoc(PandocPlugin.Format format, boolean attachment, String templateName, OutputStream out, Object... args) {
		Scope.RenderArgs templateBinding = Scope.RenderArgs.current();
        for (Object o : args) {
            List<String> names = LocalvariablesNamesEnhancer.LocalVariablesNamesTracer.getAllLocalVariableNames(o);
            for (String name : names) {
                templateBinding.put(name, o);
            }
        }
        templateBinding.put("session", Scope.Session.current());
        templateBinding.put("request", Http.Request.current());
        templateBinding.put("flash", Scope.Flash.current());
        templateBinding.put("params", Scope.Params.current());
        try {
            templateBinding.put("errors", Validation.errors());
        } catch (Exception ex) {
            throw new UnexpectedException(ex);
        }
		try {
            if (out == null){
            	// we're rendering to the current Response object
            	throw new RenderPandocTemplate(format, attachment, templateName, templateBinding.data);
            }
			else {
            	RenderPandocTemplate renderer = new RenderPandocTemplate(format, attachment, templateName, templateBinding.data);
            	renderer.writePandoc(out, Http.Request.current(), Http.Response.current());
            }
        } catch (TemplateNotFoundException ex) {
            if (ex.isSourceAvailable()) {
                throw ex;
            }
            StackTraceElement element = PlayException.getInterestingStrackTraceElement(ex);
            if (element != null) {
                throw new TemplateNotFoundException(ex.getPath(),
                		Play.classes.getApplicationClass(element.getClassName()), element.getLineNumber());
            } else {
                throw ex;
            }
        }
	}

}

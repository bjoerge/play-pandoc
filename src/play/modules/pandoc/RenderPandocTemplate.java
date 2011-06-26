package play.modules.pandoc;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import play.Logger;
import play.Play;
import play.exceptions.TemplateNotFoundException;
import play.exceptions.UnexpectedException;
import play.mvc.Http;
import play.mvc.results.Result;
import play.templates.Template;
import play.templates.TemplateLoader;
import play.vfs.VirtualFile;

import java.io.OutputStream;
import java.util.Map;

/**
 * @author: bjoerge
 * @created 6/24/11 9:06 PM
 */
public class RenderPandocTemplate extends Result {

	private String templateString;
	private PandocPlugin.Format format;
	private String templateName;
	private boolean attachment = false;

	public RenderPandocTemplate(PandocPlugin.Format format, boolean attachment, String templateName, Map<String, Object> args) throws TemplateNotFoundException {
		Http.Request request = Http.Request.current();
		templateName = resolveTemplateName(templateName, request, request.format);
		Template template = TemplateLoader.load(templateName);
		this.attachment = attachment;
		this.templateName = FilenameUtils.getBaseName(templateName);
		this.templateString = template.render(args);
		this.format = format;
    }

	static String resolveTemplateName(String templateName, Http.Request request, String format) {
        if (templateName.startsWith("@")) {
            templateName = templateName.substring(1);
            if (!templateName.contains(".")) {
                templateName = request.controller + "." + templateName;
            }
            templateName = templateName.replace(".", "/") + "." + (format == null ? "html" : format);
        }
        VirtualFile template = Play.getVirtualFile(templateName);
        if (template == null || !template.exists()) {
            if (templateName.lastIndexOf("." + format) != -1) {
            	templateName = templateName.substring(0, templateName.lastIndexOf("." + format)) + ".html";
            }
        }
        return templateName;
	}

	public void apply(Http.Request request, Http.Response response) {
        try {
			if (PandocPlugin.isPandocSupported() && format != null && PandocPlugin.supportedFormats.contains(format)) {
				String fileName = templateName+(format.extension == null ? "" : "."+format.extension);
				response.setHeader("Content-Disposition",
							String.format("%s; filename=\"%s\"", attachment ? "attachment" : "inline", fileName));
				setContentTypeIfNotSet(response, format.mimetype + ";charset=utf-8");
				renderPandoc(response.out);
			}
			else {
				Logger.warn("Pandoc support or support for format '"+format+"' is not installed, rendering as HTML instead");
				setContentTypeIfNotSet(response, "text/html;charset=utf-8");
				response.out.write(templateString.getBytes("UTF-8"));
			}
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }

	private void renderPandoc(OutputStream out) throws Exception {
        if(templateString != null){
        	renderFormat(templateString, out);
        }
	}

	private void renderFormat(String templateString, OutputStream out) throws Exception {

		ProcessBuilder builder = new ProcessBuilder(
				PandocPlugin.pandocExecutable,
				"--email-obfuscation=none",
				"--html5",
				"-s",
				"-fhtml",
				"-t"+format.identifier
		);
		final Process process = builder.start();
		OutputStream stdin = process.getOutputStream();
		stdin.write(templateString.getBytes("UTF-8"));
		stdin.flush();
		stdin.close();
		process.waitFor();
		IOUtils.copy(process.getInputStream(), out);
		IOUtils.copy(process.getErrorStream(), System.err);
		int val;
		while ((val = process.getInputStream().read()) != -1)
			out.write(val);
		out.flush();
		process.destroy();

	}

	public void writePandoc(OutputStream out, Http.Request request, Http.Response response) {
        try {
            renderPandoc(out);
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }


}
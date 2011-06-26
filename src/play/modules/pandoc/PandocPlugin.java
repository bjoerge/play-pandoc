package play.modules.pandoc;

import play.Logger;
import play.Play;
import play.PlayPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: bjoerge
 * @created 6/25/11 6:47 PM
 */
public class PandocPlugin extends PlayPlugin {

	public static enum Format {
		NATIVE("native", "Native Haskell", "text/plain", "o"),
		JSON("json", "JSON", "application/json", "json"),
		HTML("html", "HTML Source", "text/plain", "html"),
		PLAIN("plain", "Plain text", "text/plain", "txt"),
		MARKDOWN("markdown", "Markdown", "text/x-web-markdown", "md"),
		RST("rst", "reStructuredText", "text/plain", "rst"),
		LATEX("latex", "LaTeX", "application/x-latex", "tex"),
		CONTEXT("context", "ConTeXt", "text/context", "txt"),
		MAN("man", "man (groff man)", "application/x-troff-man", "txt"),
		MEDIAWIKI("mediawiki", "MediaWiki markup", "text/mediawiki", "txt"),
		TEXTILE("textile", "Textile","text/x-web-textile", "textile"),
		TEXINFO("texinfo", "GNU Texinfo", "application/x-texinfo", "texi"),
//		DOCBOOK("docbook", "DocBook XML", "application/docbook+xml", "xml"),
//		OPENDOCUMENT("opendocument", "OpenDocument XML", "application/vnd.oasis.opendocument.text", "odt"),
		RTF("rtf", "RTF", "application/rtf", "rtf");

		public final String identifier;
		public final String mimetype;
		public final String description;
		public final String extension;

		Format(String identifier, String description, String mimetype, String extension) {
			this.identifier = identifier;
			this.description = description;
			this.mimetype = mimetype;
			this.extension = extension;
		}

		public static Format fromString(String identifier) {			for (Format f : Format.values()) {
				if (f.identifier.equalsIgnoreCase(identifier))
					return f;
			}
			throw new EnumConstantNotPresentException(Format.class, identifier);
		}
		public static boolean contains(String format) {
			for (Format f : Format.values()) {
				if (f.identifier.equalsIgnoreCase(format))
					return true;
			}
			throw new EnumConstantNotPresentException(Format.class, format);
		}
	}
	public static final String PLUGIN_VERSION = "0.1";

	static String pandocExecutable;

	static Boolean pandocSupport = false;
	static String pandocVersion = "N/A";
	static List<Format> supportedFormats;

	public static boolean isPandocSupported() {
		return pandocSupport;
	}

	private static String msg_(String msg, Object... args) {
		return String.format("PandocPlugin-" + PLUGIN_VERSION + "> %1$s",
				String.format(msg, args));
	}

	@Override
	public void onLoad() {
		try {

			Logger.debug(msg_("Checking for pandoc support"));
			pandocExecutable = Play.configuration.getProperty("pandoc.executable", "pandoc");
			Process p = Runtime.getRuntime().exec(String.format("%s -v", pandocExecutable));
			p.waitFor();
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line = reader.readLine();
			Pattern regex = Pattern.compile("pandoc (\\d+.*)");
			Matcher m = regex.matcher(line);
			if (!m.matches()) {
				throw new ExceptionInInitializerError("Pandoc command executed but unable to determine version");
			}
			pandocVersion = m.group(1);
			Logger.debug(msg_("Found pandoc " + pandocVersion));

			List<String> formats = Arrays.asList(findSupportedFormats());
			supportedFormats = new ArrayList<Format>();
			for (Format f : Format.values()) {
				if (!formats.contains(f.identifier)) {
					Logger.warn(msg_("Output format %s is supported by PandocPlugin but not by current installed version of Pandoc (%s)",
							f.identifier, pandocVersion));
				}
				else
					supportedFormats.add(f);
			}
			pandocSupport = true;
		} catch (IOException e) {
			Logger.warn(msg_("Pandoc support is required but not found. renderFormat methods will return html. Please refer to http://johnmacfarlane.net/pandoc/installing.html for installing Pandoc on your system."));
		} catch (ExceptionInInitializerError e) {
			Logger.warn(msg_("Pandoc support found but unable to determine version and supported formats: %s", e.getMessage()));
		} catch (InterruptedException e) {
			Logger.warn(msg_("Got InterruptedException when trying to figure out whether Pandoc is supported: %s", e.getMessage()));
		}
	}
	private static String[] findSupportedFormats() {
		try {
			Process p = Runtime.getRuntime().exec(pandocExecutable+" -h");
			p.waitFor();
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line = null;
			Pattern regex = Pattern.compile("Output formats:\\s*(.+)");
			while ((line = reader.readLine()) != null) {
				Matcher matcher = regex.matcher(line);
				if (matcher.matches()) {
					return matcher.group(1).split(",(\\s*)");
				}
			}
		} catch (IOException e) {
			throw new ExceptionInInitializerError("Unable to figure out formats supported by current installed version of Pandoc: "+e.getMessage());
		}
		 catch (InterruptedException e) {
			throw new ExceptionInInitializerError("Unable to figure out formats supported by current installed version of Pandoc: "+e.getMessage());
		}
		throw new ExceptionInInitializerError("Unable to figure out formats supported by current installed version of Pandoc: ");
	}
}

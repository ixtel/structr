/**
 * Copyright (C) 2010-2016 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.web.maintenance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.ResourceAccess;
import org.structr.core.graph.MaintenanceCommand;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.NodeServiceCommand;
import org.structr.core.graph.Tx;
import org.structr.core.property.PropertyMap;
import org.structr.core.script.Scripting;
import org.structr.rest.resource.MaintenanceParameterResource;
import org.structr.schema.action.ActionContext;
import org.structr.schema.export.StructrSchema;
import org.structr.schema.json.JsonSchema;
import org.structr.web.common.RenderContext;
import org.structr.web.entity.AbstractFile;
import org.structr.web.entity.FileBase;
import org.structr.web.entity.Folder;
import org.structr.web.entity.User;
import org.structr.web.entity.dom.Content;
import org.structr.web.entity.dom.DOMNode;
import org.structr.web.entity.dom.Page;
import org.structr.web.entity.dom.ShadowDocument;
import org.structr.web.entity.dom.Template;
import org.structr.web.maintenance.deploy.ComponentImportVisitor;
import org.structr.web.maintenance.deploy.FileImportVisitor;
import org.structr.web.maintenance.deploy.PageImportVisitor;
import org.structr.web.maintenance.deploy.SchemaImportVisitor;
import org.structr.web.maintenance.deploy.TemplateImportVisitor;

/**
 *
 */
public class DeployCommand extends NodeServiceCommand implements MaintenanceCommand {

	private static final Logger logger = LoggerFactory.getLogger(BulkMoveUnusedFilesCommand.class.getName());

	static {

		MaintenanceParameterResource.registerMaintenanceCommand("deploy", DeployCommand.class);
	}

	@Override
	public void execute(final Map<String, Object> attributes) throws FrameworkException {

		final String mode = (String) attributes.get("mode");
		if (mode != null && "export".equals(mode)) {

			doExport(attributes);

		} else {

			// default is "import"
			doImport(attributes);
		}
	}

	@Override
	public boolean requiresEnclosingTransaction() {
		return false;
	}

	// ----- private methods -----
	private void doImport(final Map<String, Object> attributes) throws FrameworkException {

		final String path                        = (String) attributes.get("source");
		final Map<String, Object> componentsConf = new HashMap<>();
		final Map<String, Object> templatesConf  = new HashMap<>();
		final Map<String, Object> pagesConf      = new HashMap<>();
		final Map<String, Object> filesConf      = new HashMap<>();

		if (StringUtils.isBlank(path)) {

			throw new FrameworkException(422, "Please provide source path for deployment.");
		}

		final Path source = Paths.get(path);
		if (!Files.exists(source)) {

			throw new FrameworkException(422, "Source path " + path + " does not exist.");
		}

		if (!Files.isDirectory(source)) {

			throw new FrameworkException(422, "Source path " + path + " is not a directory.");
		}

		// read users.json
		final Path usersConf = source.resolve("security/users.json");
		if (Files.exists(usersConf)) {

			logger.info("Reading {}..", usersConf);
			importMapData(User.class, readConfigMap(usersConf));
		}

		// read grants.json
		final Path grantsConf = source.resolve("security/grants.json");
		if (Files.exists(grantsConf)) {

			logger.info("Reading {}..", grantsConf);
			importListData(ResourceAccess.class, readConfigList(grantsConf));
		}

		// read files.conf
		final Path filesConfFile = source.resolve("files.json");
		if (Files.exists(filesConfFile)) {

			logger.info("Reading {}..", filesConfFile);
			filesConf.putAll(readConfigMap(filesConfFile));
		}

		// read pages.conf
		final Path pagesConfFile = source.resolve("pages.json");
		if (Files.exists(pagesConfFile)) {

			logger.info("Reading {}..", pagesConfFile);
			pagesConf.putAll(readConfigMap(pagesConfFile));
		}

		// read components.conf
		final Path componentsConfFile = source.resolve("components.json");
		if (Files.exists(componentsConfFile)) {

			logger.info("Reading {}..", componentsConfFile);
			componentsConf.putAll(readConfigMap(componentsConfFile));
		}

		// read templates.conf
		final Path templatesConfFile = source.resolve("templates.json");
		if (Files.exists(templatesConfFile)) {

			logger.info("Reading {}..", templatesConfFile);
			templatesConf.putAll(readConfigMap(templatesConfFile));
		}

		// import schema
		final Path schema = source.resolve("schema");
		if (Files.exists(schema)) {

			try {

				logger.info("Importing data from schema/ directory..");
				Files.walkFileTree(schema, new SchemaImportVisitor(schema));

			} catch (IOException ioex) {
				logger.warn("Exception while importing schema", ioex);
			}
		}

		// import files
		final Path files = source.resolve("files");
		if (Files.exists(files)) {

			try {

				logger.info("Importing files...");
				Files.walkFileTree(files, new FileImportVisitor(files, filesConf));

			} catch (IOException ioex) {
				logger.warn("Exception while importing files", ioex);
			}
		}

		// import components, must be done before pages so the shared components exist
		final Path templates = source.resolve("templates");
		if (Files.exists(templates)) {

			try {

				logger.info("Importing templates..");
				Files.walkFileTree(templates, new TemplateImportVisitor(templatesConf));

			} catch (IOException ioex) {
				logger.warn("Exception while importing templates", ioex);
			}
		}

		// import components, must be done before pages so the shared components exist
		final Path components = source.resolve("components");
		if (Files.exists(components)) {

			try {

				logger.info("Importing shared components..");
				Files.walkFileTree(components, new ComponentImportVisitor(componentsConf));

			} catch (IOException ioex) {
				logger.warn("Exception while importing shared components", ioex);
			}
		}

		// import pages
		final Path pages = source.resolve("pages");
		if (Files.exists(pages)) {

			try {

				logger.info("Importing pages..");
				Files.walkFileTree(pages, new PageImportVisitor(pages, pagesConf));

			} catch (IOException ioex) {
				logger.warn("Exception while importing pages", ioex);
			}
		}

		// apply configuration
		final Path conf = source.resolve("deploy.conf");
		if (Files.exists(conf)) {

			try (final Tx tx = StructrApp.getInstance().tx()) {

				logger.info("Applying configuration from {}..", conf);

				final String confSource = new String(Files.readAllBytes(conf), Charset.forName("utf-8"));
				Scripting.evaluate(new ActionContext(SecurityContext.getSuperUserInstance()), null, confSource.trim());

				tx.success();

			} catch (Throwable t) {
				t.printStackTrace();
			}
		}

		logger.info("Import from {} done.", source.toString());
	}

	private void doExport(final Map<String, Object> attributes) throws FrameworkException {

		final String path  = (String) attributes.get("target");
		final Path target  = Paths.get(path);

		if (Files.exists(target)) {

			throw new FrameworkException(422, "Target directory already exists, aborting.");
		}

		try {

			Files.createDirectories(target);

			final Path components     = Files.createDirectory(target.resolve("components"));
			final Path files          = Files.createDirectory(target.resolve("files"));
			final Path pages          = Files.createDirectory(target.resolve("pages"));
			final Path schema         = Files.createDirectory(target.resolve("schema"));
			final Path security       = Files.createDirectory(target.resolve("security"));
			final Path templates      = Files.createDirectory(target.resolve("templates"));
			final Path schemaJson     = schema.resolve("schema.json");
			final Path grants         = security.resolve("grants.json");
			final Path filesConf      = target.resolve("files.json");
			final Path pagesConf      = target.resolve("pages.json");
			final Path componentsConf = target.resolve("components.json");
			final Path templatesConf  = target.resolve("templates.json");

			exportFiles(files, filesConf);
			exportPages(pages, pagesConf);
			exportComponents(components, componentsConf);
			exportTemplates(templates, templatesConf);
			exportSecurity(grants);
			exportSchema(schemaJson);

			// config import order is "users, grants, pages, components, templates"
			// data import order is "schema, files, templates, components, pages"

		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private void exportFiles(final Path target, final Path configTarget) throws FrameworkException {

		final Map<String, Object> config = new LinkedHashMap<>();
		final App app                    = StructrApp.getInstance();

		try (final Tx tx = app.tx()) {

			// fetch toplevel folders and recurse
			for (final Folder folder : app.nodeQuery(Folder.class).and(Folder.parent, null).getAsList()) {
				exportFilesAndFolders(target, folder, config);
			}

			// fetch toplevel files
			for (final FileBase file : app.nodeQuery(FileBase.class).and(Folder.parent, null).getAsList()) {
				exportFile(target, file, config);
			}

			tx.success();

		} catch (IOException ioex) {
			ioex.printStackTrace();
		}

		try (final Writer fos = new OutputStreamWriter(new FileOutputStream(configTarget.toFile()))) {

			getGson().toJson(config, fos);

		} catch (IOException ioex) {
			ioex.printStackTrace();
		}
	}

	private void exportFilesAndFolders(final Path target, final Folder folder, final Map<String, Object> config) throws IOException {

		final Map<String, Object> properties = new LinkedHashMap<>();
		final String name                    = folder.getName();
		final Path path                      = target.resolve(name);

		Files.createDirectories(path);

		exportFileConfiguration(folder, properties);

		if (!properties.isEmpty()) {
			config.put(folder.getPath(), properties);
		}

		for (final Folder child : folder.getProperty(Folder.folders)) {
			exportFilesAndFolders(path, child, config);
		}

		for (final FileBase file : folder.getProperty(Folder.files)) {
			exportFile(path, file, config);
		}
	}

	private void exportFile(final Path target, final FileBase file, final Map<String, Object> config) throws IOException {

		final Map<String, Object> properties = new LinkedHashMap<>();
		final String name                    = file.getName();
		final Path src                       = file.getFileOnDisk().toPath();
		Path path                            = target.resolve(name);
		int i                                = 0;

		// modify file name if there are duplicates in the database
		while (Files.exists(path)) {
			path = target.resolve(name + i++);
		}

		try {
			Files.copy(src, path);

		} catch (IOException ioex) {
			// ignore this
		}

		exportFileConfiguration(file, properties);

		if (!properties.isEmpty()) {
			config.put(file.getPath(), properties);
		}
	}

	private void exportPages(final Path target, final Path configTarget) throws FrameworkException {

		final Map<String, Object> pagesConfig = new LinkedHashMap<>();
		final App app                         = StructrApp.getInstance();

		try (final Tx tx = app.tx()) {

			for (final Page page : app.nodeQuery(Page.class).getAsList()) {

				if (!(page instanceof ShadowDocument)) {

					final String content = page.getContent(RenderContext.EditMode.DEPLOYMENT);
					if (content != null) {

						final Map<String, Object> properties = new LinkedHashMap<>();
						final String name                    = page.getName();
						final Path pageFile                  = target.resolve(name + ".html");

						pagesConfig.put(name, properties);
						exportConfiguration(page, properties);

						try (final OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(pageFile.toFile()))) {

							writer.write(content);
							writer.flush();
							writer.close();

						} catch (IOException ioex) {
							ioex.printStackTrace();
						}
					}
				}
			}

			tx.success();
		}

		try (final Writer fos = new OutputStreamWriter(new FileOutputStream(configTarget.toFile()))) {

			getGson().toJson(pagesConfig, fos);

		} catch (IOException ioex) {
			ioex.printStackTrace();
		}
	}

	private void exportComponents(final Path target, final Path configTarget) throws FrameworkException {

		final Map<String, Object> configuration = new LinkedHashMap<>();
		final App app                           = StructrApp.getInstance();

		try (final Tx tx = app.tx()) {

			final ShadowDocument shadowDocument = app.nodeQuery(ShadowDocument.class).getFirst();
			if (shadowDocument != null) {

				for (final DOMNode node : shadowDocument.getProperty(Page.elements)) {

					// skip templates, nodes in trash and non-toplevel nodes
					if (node instanceof Content || node.inTrash() || node.getProperty(DOMNode.parent) != null) {
						continue;
					}

					final Map<String, Object> properties = new LinkedHashMap<>();

					String name = node.getProperty(AbstractNode.name);
					if (name == null) {

						name = node.getUuid();
					}

					configuration.put(name, properties);
					exportConfiguration(node, properties);

					final String content = node.getContent(RenderContext.EditMode.DEPLOYMENT);
					if (content != null) {

						final Path pageFile = target.resolve(name + ".html");

						try (final OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(pageFile.toFile()))) {

							writer.write(content);
							writer.flush();
							writer.close();

						} catch (IOException ioex) {
							ioex.printStackTrace();
						}
					}
				}
			}

			tx.success();
		}

		try (final Writer fos = new OutputStreamWriter(new FileOutputStream(configTarget.toFile()))) {

			getGson().toJson(configuration, fos);

		} catch (IOException ioex) {
			ioex.printStackTrace();
		}
	}

	private void exportTemplates(final Path target, final Path configTarget) throws FrameworkException {

		final Map<String, Object> configuration = new LinkedHashMap<>();
		final App app                           = StructrApp.getInstance();

		try (final Tx tx = app.tx()) {

			// export template nodes anywhere in the pages tree or shared components view
			for (final Template template : app.nodeQuery(Template.class).getAsList()) {

				if (template.inTrash()) {
					continue;
				}

				exportTemplateSource(target, template, configuration);
			}

			final ShadowDocument shadowDocument = app.nodeQuery(ShadowDocument.class).getFirst();
			if (shadowDocument != null) {

				for (final DOMNode node : shadowDocument.getProperty(Page.elements)) {

					// skip everything except templates, skip nodes in trash and non-toplevel nodes
					if (!(node instanceof Content) || node.inTrash() || node.getProperty(DOMNode.parent) != null) {
						continue;
					}

					exportTemplateSource(target, node, configuration);
				}
			}

			tx.success();
		}

		try (final Writer fos = new OutputStreamWriter(new FileOutputStream(configTarget.toFile()))) {

			getGson().toJson(configuration, fos);

		} catch (IOException ioex) {
			ioex.printStackTrace();
		}
	}

	private void exportTemplateSource(final Path target, final DOMNode template, final Map<String, Object> configuration) {

		final Map<String, Object> properties = new LinkedHashMap<>();

		// name or uuid
		String name = template.getProperty(AbstractNode.name);
		if (name == null) {

			name = template.getUuid();
		}

		configuration.put(name, properties);
		exportConfiguration(template, properties);

		final String content = template.getProperty(Template.content);
		if (content != null) {

			final Path pageFile = target.resolve(name + ".html");

			try (final OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(pageFile.toFile()))) {

				writer.write(content);
				writer.flush();
				writer.close();

			} catch (IOException ioex) {
				ioex.printStackTrace();
			}
		}
	}

	private void exportSecurity(final Path target) throws FrameworkException {

		final List<Map<String, Object>> grants = new LinkedList<>();
		final App app                          = StructrApp.getInstance();

		try (final Tx tx = app.tx()) {

			for (final ResourceAccess res : app.nodeQuery(ResourceAccess.class).getAsList()) {

				final Map<String, Object> grant = new LinkedHashMap<>();
				grants.add(grant);

				grant.put("signature", res.getProperty(ResourceAccess.signature));
				grant.put("flags",     res.getProperty(ResourceAccess.flags));
			}

			tx.success();
		}

		try (final Writer fos = new OutputStreamWriter(new FileOutputStream(target.toFile()))) {

			getGson().toJson(grants, fos);

		} catch (IOException ioex) {
			ioex.printStackTrace();
		}
	}

	private void exportSchema(final Path target) throws FrameworkException {

		try {

			final JsonSchema schema = StructrSchema.createFromDatabase(StructrApp.getInstance());

			try (final Writer writer = new FileWriter(target.toFile())) {

				writer.append(schema.toString());
				writer.append("\n");
				writer.flush();

			} catch (IOException ioex) {
				ioex.printStackTrace();
			}

		} catch (URISyntaxException x) {
			x.printStackTrace();
		}
	}

	private void exportConfiguration(final DOMNode node, final Map<String, Object> config) {

		putIf(config, "visibleToPublicUsers",        node.isVisibleToPublicUsers());
		putIf(config, "visibleToAuthenticatedUsers", node.isVisibleToAuthenticatedUsers());
		putIf(config, "contentType",                 node.getProperty(Content.contentType));
		putIf(config, "position",                    node.getProperty(Page.position));
		putIf(config, "showOnErrorCodes",            node.getProperty(Page.showOnErrorCodes));
		putIf(config, "showConditions",              node.getProperty(Page.showConditions));
		putIf(config, "hideConditions",              node.getProperty(Page.hideConditions));
		putIf(config, "dontCache",                   node.getProperty(Page.dontCache));
		putIf(config, "cacheForSeconds",             node.getProperty(Page.cacheForSeconds));
		putIf(config, "pageCreatesRawData",          node.getProperty(Page.pageCreatesRawData));
	}

	private void exportFileConfiguration(final AbstractFile file, final Map<String, Object> config) {

		putIf(config, "visibleToPublicUsers",        file.isVisibleToPublicUsers());
		putIf(config, "visibleToAuthenticatedUsers", file.isVisibleToAuthenticatedUsers());
		putIf(config, "contentType",                 file.getProperty(Content.contentType));
		putIf(config, "dontCache",                   file.getProperty(Page.dontCache));
		putIf(config, "cacheForSeconds",             file.getProperty(Page.cacheForSeconds));
	}

	private void putIf(final Map<String, Object> target, final String key, final Object value) {

		if (value != null) {
			target.put(key, value);
		}
	}

	private Map<String, Object> readConfigMap(final Path pagesConf) {

		try (final Reader reader = Files.newBufferedReader(pagesConf, Charset.forName("utf-8"))) {

			return getGson().fromJson(reader, Map.class);

		} catch (IOException ioex) {
			ioex.printStackTrace();
		}

		return Collections.emptyMap();
	}

	private List<Map<String, Object>> readConfigList(final Path pagesConf) {

		try (final Reader reader = Files.newBufferedReader(pagesConf, Charset.forName("utf-8"))) {

			return getGson().fromJson(reader, List.class);

		} catch (IOException ioex) {
			ioex.printStackTrace();
		}

		return Collections.emptyList();
	}

	private <T extends NodeInterface> void importMapData(final Class<T> type, final Map<String, Object> data) throws FrameworkException {

		final SecurityContext context = SecurityContext.getSuperUserInstance();
		final App app                 = StructrApp.getInstance();

		try (final Tx tx = app.tx()) {

			for (final T toDelete : app.nodeQuery(type).getAsList()) {
				app.delete(toDelete);
			}

			for (final Entry<String, Object> entry : data.entrySet()) {

				final String key = entry.getKey();
				final Object val = entry.getValue();

				if (val instanceof Map) {

					final Map<String, Object> values = (Map<String, Object>)val;
					final PropertyMap properties     = PropertyMap.inputTypeToJavaType(context, type, values);

					properties.put(AbstractNode.name, key);

					app.create(type, properties);
				}
			}

			tx.success();
		}
	}

	private <T extends NodeInterface> void importListData(final Class<T> type, final List<Map<String, Object>> data) throws FrameworkException {

		final SecurityContext context = SecurityContext.getSuperUserInstance();
		final App app                 = StructrApp.getInstance();

		try (final Tx tx = app.tx()) {

			for (final T toDelete : app.nodeQuery(type).getAsList()) {
				app.delete(toDelete);
			}

			for (final Map<String, Object> entry : data) {

				app.create(type, PropertyMap.inputTypeToJavaType(context, type, entry));
			}

			tx.success();
		}
	}

	private Gson getGson() {
		return new GsonBuilder().setPrettyPrinting().create();
	}
}

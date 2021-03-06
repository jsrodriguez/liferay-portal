/**
 * Copyright (c) 2000-2013 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portlet.documentlibrary.lar;

import com.liferay.portal.kernel.dao.orm.ActionableDynamicQuery;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.Property;
import com.liferay.portal.kernel.dao.orm.PropertyFactoryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.lar.BasePortletDataHandler;
import com.liferay.portal.kernel.lar.ExportImportPathUtil;
import com.liferay.portal.kernel.lar.PortletDataContext;
import com.liferay.portal.kernel.lar.PortletDataHandlerBoolean;
import com.liferay.portal.kernel.lar.PortletDataHandlerControl;
import com.liferay.portal.kernel.lar.StagedModelDataHandlerUtil;
import com.liferay.portal.kernel.lar.StagedModelType;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.repository.model.Folder;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.model.Portlet;
import com.liferay.portal.service.PortletLocalServiceUtil;
import com.liferay.portal.util.PropsValues;
import com.liferay.portlet.documentlibrary.model.DLFileEntry;
import com.liferay.portlet.documentlibrary.model.DLFileEntryType;
import com.liferay.portlet.documentlibrary.model.DLFileRank;
import com.liferay.portlet.documentlibrary.model.DLFileShortcut;
import com.liferay.portlet.documentlibrary.model.DLFolder;
import com.liferay.portlet.documentlibrary.model.DLFolderConstants;
import com.liferay.portlet.documentlibrary.service.DLAppLocalServiceUtil;
import com.liferay.portlet.documentlibrary.service.permission.DLPermission;
import com.liferay.portlet.documentlibrary.service.persistence.DLFileEntryExportActionableDynamicQuery;
import com.liferay.portlet.documentlibrary.service.persistence.DLFileEntryTypeExportActionableDynamicQuery;
import com.liferay.portlet.documentlibrary.service.persistence.DLFileShortcutExportActionableDynamicQuery;
import com.liferay.portlet.documentlibrary.service.persistence.DLFolderExportActionableDynamicQuery;

import java.util.List;
import java.util.Map;

import javax.portlet.PortletPreferences;

/**
 * @author Bruno Farache
 * @author Raymond Augé
 * @author Sampsa Sohlman
 * @author Mate Thurzo
 * @author Zsolt Berentey
 */
public class DLPortletDataHandler extends BasePortletDataHandler {

	public static final String NAMESPACE = "document_library";

	public DLPortletDataHandler() {
		setDataLocalized(true);
		setDataPortletPreferences("rootFolderId");
		setDeletionSystemEventStagedModelTypes(
			new StagedModelType(DLFileEntryType.class),
			new StagedModelType(DLFileRank.class),
			new StagedModelType(DLFileShortcut.class),
			new StagedModelType(FileEntry.class),
			new StagedModelType(Folder.class));
		setExportControls(
			new PortletDataHandlerBoolean(
				NAMESPACE, "documents", true, false,
				new PortletDataHandlerControl[] {
					new PortletDataHandlerBoolean(
						NAMESPACE, "previews-and-thumbnails")
				},
				FileEntry.class.getName()),
			new PortletDataHandlerBoolean(
				NAMESPACE, "shortcuts", true, false, null,
				DLFileShortcut.class.getName()));
		setPublishToLiveByDefault(PropsValues.DL_PUBLISH_TO_LIVE_BY_DEFAULT);
	}

	@Override
	protected PortletPreferences doDeleteData(
			PortletDataContext portletDataContext, String portletId,
			PortletPreferences portletPreferences)
		throws Exception {

		if (portletDataContext.addPrimaryKey(
				DLPortletDataHandler.class, "deleteData")) {

			return portletPreferences;
		}

		DLAppLocalServiceUtil.deleteAll(portletDataContext.getScopeGroupId());

		return portletPreferences;
	}

	@Override
	protected String doExportData(
			final PortletDataContext portletDataContext, String portletId,
			PortletPreferences portletPreferences)
		throws Exception {

		portletDataContext.addPermissions(
			DLPermission.RESOURCE_NAME, portletDataContext.getScopeGroupId());

		Element rootElement = addExportDataRootElement(portletDataContext);

		rootElement.addAttribute(
			"group-id", String.valueOf(portletDataContext.getScopeGroupId()));

		if (portletDataContext.getBooleanParameter(NAMESPACE, "documents")) {
			ActionableDynamicQuery fileEntryTypeActionableDynamicQuery =
				getDLFileEntryTypeActionableDynamicQuery(portletDataContext);

			fileEntryTypeActionableDynamicQuery.performActions();

			ActionableDynamicQuery folderActionableDynamicQuery =
				getFolderActionableDynamicQuery(portletDataContext);

			folderActionableDynamicQuery.performActions();

			ActionableDynamicQuery fileEntryActionableDynamicQuery =
				getFileEntryActionableDynamicQuery(portletDataContext);

			fileEntryActionableDynamicQuery.performActions();
		}

		if (portletDataContext.getBooleanParameter(NAMESPACE, "shortcuts")) {
			ActionableDynamicQuery fileShortcutActionableDynamicQuery =
				getDLFileShortcutActionableDynamicQuery(portletDataContext);

			fileShortcutActionableDynamicQuery.performActions();
		}

		return getExportDataRootElementString(rootElement);
	}

	@Override
	protected PortletPreferences doImportData(
			PortletDataContext portletDataContext, String portletId,
			PortletPreferences portletPreferences, String data)
		throws Exception {

		portletDataContext.importPermissions(
			DLPermission.RESOURCE_NAME, portletDataContext.getSourceGroupId(),
			portletDataContext.getScopeGroupId());

		Element fileEntryTypesElement =
			portletDataContext.getImportDataGroupElement(DLFileEntryType.class);

		List<Element> fileEntryTypeElements = fileEntryTypesElement.elements();

		for (Element fileEntryTypeElement : fileEntryTypeElements) {
			StagedModelDataHandlerUtil.importStagedModel(
				portletDataContext, fileEntryTypeElement);
		}

		if (portletDataContext.getBooleanParameter(NAMESPACE, "documents")) {
			Element foldersElement =
				portletDataContext.getImportDataGroupElement(Folder.class);

			List<Element> folderElements = foldersElement.elements();

			for (Element folderElement : folderElements) {
				StagedModelDataHandlerUtil.importStagedModel(
					portletDataContext, folderElement);
			}

			Element fileEntriesElement =
				portletDataContext.getImportDataGroupElement(FileEntry.class);

			List<Element> fileEntryElements = fileEntriesElement.elements();

			for (Element fileEntryElement : fileEntryElements) {
				StagedModelDataHandlerUtil.importStagedModel(
					portletDataContext, fileEntryElement);
			}
		}

		if (portletDataContext.getBooleanParameter(NAMESPACE, "shortcuts")) {
			Element fileShortcutsElement =
				portletDataContext.getImportDataGroupElement(
					DLFileShortcut.class);

			List<Element> fileShortcutElements =
				fileShortcutsElement.elements();

			for (Element fileShortcutElement : fileShortcutElements) {
				StagedModelDataHandlerUtil.importStagedModel(
					portletDataContext, fileShortcutElement);
			}
		}

		return portletPreferences;
	}

	@Override
	protected void doPrepareManifestSummary(
			final PortletDataContext portletDataContext,
			PortletPreferences portletPreferences)
		throws Exception {

		ActionableDynamicQuery dlFileShortcutActionableDynamicQuery =
			getDLFileShortcutActionableDynamicQuery(portletDataContext);

		dlFileShortcutActionableDynamicQuery.performCount();

		ActionableDynamicQuery fileEntryActionableDynamicQuery =
			getFileEntryActionableDynamicQuery(portletDataContext);

		fileEntryActionableDynamicQuery.performCount();

		ActionableDynamicQuery folderActionableDynamicQuery =
			getFolderActionableDynamicQuery(portletDataContext);

		folderActionableDynamicQuery.performCount();
	}

	@Override
	protected PortletPreferences doProcessExportPortletPreferences(
			PortletDataContext portletDataContext, String portletId,
			PortletPreferences portletPreferences, Element rootElement)
		throws Exception {

		long rootFolderId = GetterUtil.getLong(
			portletPreferences.getValue("rootFolderId", null));

		if (rootFolderId != DLFolderConstants.DEFAULT_PARENT_FOLDER_ID) {
			Folder folder = DLAppLocalServiceUtil.getFolder(rootFolderId);

			Portlet portlet = PortletLocalServiceUtil.getPortletById(
				portletDataContext.getCompanyId(), portletId);

			portletDataContext.addReferenceElement(
				portlet, rootElement, folder, Folder.class,
				PortletDataContext.REFERENCE_TYPE_DEPENDENCY,
				!portletDataContext.getBooleanParameter(
					NAMESPACE, "documents"));
		}

		return portletPreferences;
	}

	@Override
	protected PortletPreferences doProcessImportPortletPreferences(
			PortletDataContext portletDataContext, String portletId,
			PortletPreferences portletPreferences)
		throws Exception {

		long rootFolderId = GetterUtil.getLong(
			portletPreferences.getValue("rootFolderId", null));

		if (rootFolderId > 0) {
			String rootFolderPath = ExportImportPathUtil.getModelPath(
				portletDataContext, Folder.class.getName(), rootFolderId);

			Folder folder = (Folder)portletDataContext.getZipEntryAsObject(
				rootFolderPath);

			StagedModelDataHandlerUtil.importStagedModel(
				portletDataContext, folder);

			Map<Long, Long> folderIds =
				(Map<Long, Long>)portletDataContext.getNewPrimaryKeysMap(
					Folder.class);

			rootFolderId = MapUtil.getLong(
				folderIds, rootFolderId, rootFolderId);

			portletPreferences.setValue(
				"rootFolderId", String.valueOf(rootFolderId));
		}

		return portletPreferences;
	}

	protected ActionableDynamicQuery getDLFileEntryTypeActionableDynamicQuery(
			final PortletDataContext portletDataContext)
		throws Exception {

		return new DLFileEntryTypeExportActionableDynamicQuery(
			portletDataContext) {

			@Override
			protected void addCriteria(DynamicQuery dynamicQuery) {
				super.addCriteria(dynamicQuery);

				Property property = PropertyFactoryUtil.forName("groupId");

				dynamicQuery.add(
					property.in(
						new Long[] {
							portletDataContext.getCompanyGroupId(),
							portletDataContext.getScopeGroupId()
						}));
			}

			@Override
			protected void performAction(Object object) throws PortalException {
				DLFileEntryType dlFileEntryType = (DLFileEntryType)object;

				if (dlFileEntryType.isExportable()) {
					StagedModelDataHandlerUtil.exportStagedModel(
						portletDataContext, dlFileEntryType);
				}
			}

		};
	}

	protected ActionableDynamicQuery getDLFileShortcutActionableDynamicQuery(
			final PortletDataContext portletDataContext)
		throws Exception {

		return new DLFileShortcutExportActionableDynamicQuery(
			portletDataContext) {

			@Override
			protected void addCriteria(DynamicQuery dynamicQuery) {
				super.addCriteria(dynamicQuery);

				Property property = PropertyFactoryUtil.forName("active");

				dynamicQuery.add(property.eq(Boolean.TRUE));
			}

		};
	}

	protected ActionableDynamicQuery getFileEntryActionableDynamicQuery(
			final PortletDataContext portletDataContext)
		throws Exception {

		return new DLFileEntryExportActionableDynamicQuery(portletDataContext) {

			@Override
			protected void addCriteria(DynamicQuery dynamicQuery) {
				super.addCriteria(dynamicQuery);

				Property property = PropertyFactoryUtil.forName("repositoryId");

				dynamicQuery.add(
					property.eq(portletDataContext.getScopeGroupId()));
			}

			@Override
			protected StagedModelType getStagedModelType() {
				return new StagedModelType(FileEntry.class);
			}

			@Override
			protected void performAction(Object object)
				throws PortalException, SystemException {

				DLFileEntry dlFileEntry = (DLFileEntry)object;

				FileEntry fileEntry = DLAppLocalServiceUtil.getFileEntry(
					dlFileEntry.getFileEntryId());

				StagedModelDataHandlerUtil.exportStagedModel(
					portletDataContext, fileEntry);
			}

		};
	}

	protected ActionableDynamicQuery getFolderActionableDynamicQuery(
			final PortletDataContext portletDataContext)
		throws Exception {

		return new DLFolderExportActionableDynamicQuery(portletDataContext) {

			@Override
			protected void addCriteria(DynamicQuery dynamicQuery) {
				super.addCriteria(dynamicQuery);

				Property property = PropertyFactoryUtil.forName("repositoryId");

				dynamicQuery.add(
					property.eq(portletDataContext.getScopeGroupId()));
			}

			@Override
			protected StagedModelType getStagedModelType() {
				return new StagedModelType(Folder.class);
			}

			@Override
			protected void performAction(Object object)
				throws PortalException, SystemException {

				DLFolder dlFolder = (DLFolder)object;

				Folder folder = DLAppLocalServiceUtil.getFolder(
					dlFolder.getFolderId());

				StagedModelDataHandlerUtil.exportStagedModel(
					portletDataContext, folder);
			}

		};
	}

}
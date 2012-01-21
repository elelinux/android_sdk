/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.internal.editors.drawable;

import static com.android.ide.eclipse.adt.AdtConstants.EDITORS_NAMESPACE;

import com.android.ide.common.resources.ResourceFolder;
import com.android.ide.eclipse.adt.internal.editors.XmlEditorDelegate;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlCommonEditor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DocumentDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceManager;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.resources.ResourceFolderType;

import org.eclipse.core.resources.IFile;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Editor for /res/drawable XML files.
 */
@SuppressWarnings("restriction")
public class DrawableEditorDelegate extends XmlEditorDelegate {

    public static class Creator implements IXmlEditorCreator {
        @Override
        @SuppressWarnings("unchecked")
        public DrawableEditorDelegate createForFile(
                AndroidXmlCommonEditor delegator,
                IFileEditorInput input) {
            // get the IFile object and check it's the desired sub-resource folder
            IFile iFile = input.getFile();
            ResourceFolder resFolder = ResourceManager.getInstance().getResourceFolder(iFile);
            ResourceFolderType type = resFolder == null ? null : resFolder.getType();
            if (ResourceFolderType.DRAWABLE.equals(type)) {
                return new DrawableEditorDelegate(delegator);
            }

            return null;
        }
    }

    /**
     * Old standalone-editor ID.
     * @deprecated Use {@link AndroidXmlCommonEditor#ID} instead.
     */
    @Deprecated
    public static final String OLD_STANDALONE_EDITOR_ID = EDITORS_NAMESPACE + ".drawable.DrawableEditor"; //$NON-NLS-1$

    /** Root node of the UI element hierarchy */
    private UiElementNode mUiRootNode;
    /** The tag used at the root */
    private String mRootTag;

    private final AndroidXmlCommonEditor mDelegator;

    /**
     * Creates the form editor for resources XML files.
     */
    public DrawableEditorDelegate(AndroidXmlCommonEditor delegator) {
        super();
        mDelegator = delegator;
    }

    @Override
    public AndroidXmlCommonEditor getEditor() {
        return mDelegator;
    }

    @Override
    public void dispose() {
        // pass
    }

    @Override
    public UiElementNode getUiRootNode() {
        return mUiRootNode;
    }

    @Override
    public boolean isSaveAsAllowed() {
        return true;
    }

    @Override
    public void createFormPages() {
        /* Disabled for now; doesn't work quite right
        try {
            addPage(new DrawableTreePage(this));
        } catch (PartInitException e) {
            AdtPlugin.log(IStatus.ERROR, "Error creating nested page"); //$NON-NLS-1$
            AdtPlugin.getDefault().getLog().log(e.getStatus());
        }
        */
     }

    @Override
    public void setInput(IEditorInput input) {
        if (input instanceof FileEditorInput) {
            FileEditorInput fileInput = (FileEditorInput) input;
            IFile file = fileInput.getFile();
            getEditor().setPartName(String.format("%1$s", file.getName()));
        }
    }

    @Override
    public void xmlModelChanged(Document xmlDoc) {
        Element rootElement = xmlDoc.getDocumentElement();
        if (rootElement != null) {
            mRootTag = rootElement.getTagName();
        }

        initUiRootNode(false /*force*/);

        if (mRootTag != null
                && !mRootTag.equals(mUiRootNode.getDescriptor().getXmlLocalName())) {
            AndroidTargetData data = getEditor().getTargetData();
            if (data != null) {
                ElementDescriptor descriptor =
                    data.getDrawableDescriptors().getElementDescriptor(mRootTag);
                // Replace top level node now that we know the actual type

                // Disconnect from old
                mUiRootNode.setEditor(null);
                mUiRootNode.setXmlDocument(null);

                // Create new
                mUiRootNode = descriptor.createUiNode();
                mUiRootNode.setXmlDocument(xmlDoc);
                mUiRootNode.setEditor(getEditor());
            }
        }

        if (mUiRootNode.getDescriptor() instanceof DocumentDescriptor) {
            mUiRootNode.loadFromXmlNode(xmlDoc);
        } else {
            mUiRootNode.loadFromXmlNode(rootElement);
        }
    }

    @Override
    public void initUiRootNode(boolean force) {
        // The manifest UI node is always created, even if there's no corresponding XML node.
        if (mUiRootNode == null || force) {
            ElementDescriptor descriptor;
            boolean reload = false;
            AndroidTargetData data = getEditor().getTargetData();
            if (data == null) {
                descriptor = new DocumentDescriptor("temp", null /*children*/);
            } else {
                descriptor = data.getDrawableDescriptors().getElementDescriptor(mRootTag);
                reload = true;
            }
            mUiRootNode = descriptor.createUiNode();
            mUiRootNode.setEditor(getEditor());

            if (reload) {
                onDescriptorsChanged();
            }
        }
    }

    private void onDescriptorsChanged() {
        IStructuredModel model = getEditor().getModelForRead();
        if (model != null) {
            try {
                Node node = getEditor().getXmlDocument(model).getDocumentElement();
                mUiRootNode.reloadFromXmlNode(node);
            } finally {
                model.releaseFromRead();
            }
        }
    }
}
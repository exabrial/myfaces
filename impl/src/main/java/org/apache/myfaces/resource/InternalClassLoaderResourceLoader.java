/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.myfaces.resource;

import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;

import javax.faces.application.ProjectStage;
import javax.faces.application.ResourceHandler;
import javax.faces.application.ResourceVisitOption;
import javax.faces.context.FacesContext;

import org.apache.myfaces.buildtools.maven2.plugin.builder.annotation.JSFWebConfigParam;
import org.apache.myfaces.shared.resource.AliasResourceMetaImpl;
import org.apache.myfaces.shared.resource.ResourceLoader;
import org.apache.myfaces.shared.resource.ResourceMeta;
import org.apache.myfaces.shared.resource.ResourceMetaImpl;
import org.apache.myfaces.shared.util.ClassUtils;
import org.apache.myfaces.shared.util.WebConfigParamUtils;
import org.apache.myfaces.shared.renderkit.html.util.ResourceUtils;
import org.apache.myfaces.shared.resource.ClassLoaderResourceLoaderIterator;

/**
 * A resource loader implementation which loads resources from the thread ClassLoader.
 */
public class InternalClassLoaderResourceLoader extends ResourceLoader
{

    /**
     * Define the mode used for jsf.js file:
     * <ul>
     * <li>normal : contains everything, including i18n (jsf-i18n.js)</li>
     * <li>minimal: contains everything, excluding i18n (jsf-i18n.js)</li>
     * </ul>
     * <p>If org.apache.myfaces.USE_MULTIPLE_JS_FILES_FOR_JSF_UNCOMPRESSED_JS param is set to true and project stage
     * is Development, this param is ignored.</p>
     */
    @JSFWebConfigParam(since = "2.0.10,2.1.4", defaultValue = "normal",
                       expectedValues = "normal, minimal", group = "render")
    public static final String MYFACES_JSF_MODE = "org.apache.myfaces.JSF_JS_MODE";

    private final String _jsfMode;
    private final boolean _developmentStage;

    public InternalClassLoaderResourceLoader(String prefix)
    {
        super(prefix);

        _jsfMode = WebConfigParamUtils.getStringInitParameter(FacesContext.getCurrentInstance().getExternalContext(),
                    MYFACES_JSF_MODE, ResourceUtils.JSF_MYFACES_JSFJS_NORMAL);
        _developmentStage = FacesContext.getCurrentInstance().isProjectStage(ProjectStage.Development);
    }

    @Override
    public String getLibraryVersion(String path)
    {
        return null;
    }

    @Override
    public InputStream getResourceInputStream(ResourceMeta resourceMeta)
    {
        InputStream is;

        String prefix = getPrefix();
        if (prefix != null && !prefix.isEmpty())
        {
            String name = prefix + '/' + resourceMeta.getResourceIdentifier();
            is = getClassLoader().getResourceAsStream(name);
            if (is == null)
            {
                is = this.getClass().getClassLoader().getResourceAsStream(name);
            }
            return is;
        }
        else
        {
            is = getClassLoader().getResourceAsStream(resourceMeta.getResourceIdentifier());
            if (is == null)
            {
                is = this.getClass().getClassLoader().getResourceAsStream(resourceMeta.getResourceIdentifier());
            }
            return is;
        }
    }

    public URL getResourceURL(String resourceId)
    {
        URL url;

        String prefix = getPrefix();
        if (prefix != null && !prefix.isEmpty())
        {
            String name = prefix + '/' + resourceId;
            url = getClassLoader().getResource(name);
            if (url == null)
            {
                url = this.getClass().getClassLoader().getResource(name);
            }
            return url;
        }
        else
        {
            url = getClassLoader().getResource(resourceId);
            if (url == null)
            {
                url = this.getClass().getClassLoader().getResource(resourceId);
            }
            return url;
        }
    }
    
    @Override
    public URL getResourceURL(ResourceMeta resourceMeta)
    {
        return getResourceURL(resourceMeta.getResourceIdentifier());
    }

    @Override
    public String getResourceVersion(String path)
    {
        return null;
    }

    @Override
    public ResourceMeta createResourceMeta(String prefix, String libraryName, String libraryVersion,
                                           String resourceName, String resourceVersion)
    {
        //handle jsf.js
        final boolean javaxFacesLib = libraryName != null &&
                ResourceHandler.JSF_SCRIPT_LIBRARY_NAME.equals(libraryName);
        final boolean javaxFaces = javaxFacesLib &&
                ResourceHandler.JSF_SCRIPT_RESOURCE_NAME.equals(resourceName);

        if (javaxFaces)
        {
            if (_developmentStage)
            {
                    return new AliasResourceMetaImpl(prefix, libraryName, libraryVersion, resourceName, resourceVersion,
                                                     ResourceUtils.JSF_UNCOMPRESSED_FULL_JS_RESOURCE_NAME, false);
            }
            else
            {
                return new AliasResourceMetaImpl(prefix, libraryName, libraryVersion, resourceName, resourceVersion,
                        ResourceUtils.JSF_MINIMAL_JS_RESOURCE_NAME, false);
            }
        }
        else if (javaxFacesLib && !_developmentStage &&
                                   (ResourceUtils.JSF_MYFACES_JSFJS_I18N.equals(resourceName)))
        {
            return new ResourceMetaImpl(prefix, libraryName, libraryVersion, resourceName, resourceVersion);
        }
        else if (_developmentStage && libraryName != null && libraryName.startsWith("org.apache.myfaces.core"))
        {
            return new ResourceMetaImpl(prefix, libraryName, libraryVersion, resourceName, resourceVersion);
        }
        else
        {
            return null;
        }
    }

    /**
     * Returns the ClassLoader to use when looking up resources under the top level package. By default, this is the
     * context class loader.
     *
     * @return the ClassLoader used to lookup resources
     */
    protected ClassLoader getClassLoader()
    {
        return ClassUtils.getContextClassLoader();
    }

    @Override
    public boolean libraryExists(String libraryName)
    {
        String prefix = getPrefix();
        if (prefix != null && !prefix.isEmpty())
        {
            String name = prefix + '/' + libraryName;
            URL url = getClassLoader().getResource(name);
            if (url == null)
            {
                url = this.getClass().getClassLoader().getResource(name);
            }
            if (url != null)
            {
                return true;
            }
        }
        else
        {
            URL url = getClassLoader().getResource(libraryName);
            if (url == null)
            {
                url = this.getClass().getClassLoader().getResource(libraryName);
            }
            if (url != null)
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterator<String> iterator(FacesContext facesContext, String path, 
            int maxDepth, ResourceVisitOption... options)
    {
        String basePath = path;

        String prefix = getPrefix();
        if (prefix != null)
        {
            basePath = prefix + '/' + (path.startsWith("/") ? path.substring(1) : path);
        }

        URL url = getClassLoader().getResource(basePath);

        return new ClassLoaderResourceLoaderIterator(url, basePath, maxDepth, options);
    }
}

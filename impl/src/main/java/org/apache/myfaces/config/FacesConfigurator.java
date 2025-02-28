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
package org.apache.myfaces.config;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.el.ELResolver;
import jakarta.faces.FacesException;
import jakarta.faces.FacesWrapper;
import jakarta.faces.FactoryFinder;
import jakarta.faces.application.Application;
import jakarta.faces.application.ApplicationFactory;
import jakarta.faces.application.ConfigurableNavigationHandler;
import jakarta.faces.application.NavigationHandler;
import jakarta.faces.application.ProjectStage;
import jakarta.faces.application.ResourceHandler;
import jakarta.faces.application.StateManager;
import jakarta.faces.application.ViewHandler;
import jakarta.faces.component.UIViewRoot;
import jakarta.faces.component.search.SearchExpressionHandler;
import jakarta.faces.component.search.SearchKeywordResolver;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ActionListener;
import jakarta.faces.event.ComponentSystemEvent;
import jakarta.faces.event.PhaseListener;
import jakarta.faces.event.PostConstructApplicationEvent;
import jakarta.faces.event.PostConstructViewMapEvent;
import jakarta.faces.event.PreDestroyViewMapEvent;
import jakarta.faces.event.SystemEvent;
import jakarta.faces.flow.Flow;
import jakarta.faces.flow.FlowHandler;
import jakarta.faces.flow.FlowHandlerFactory;
import jakarta.faces.lifecycle.ClientWindow;
import jakarta.faces.lifecycle.Lifecycle;
import jakarta.faces.lifecycle.LifecycleFactory;
import jakarta.faces.render.RenderKit;
import jakarta.faces.render.RenderKitFactory;
import jakarta.faces.validator.BeanValidator;
import jakarta.faces.webapp.FacesServlet;

import org.apache.myfaces.application.ApplicationFactoryImpl;
import org.apache.myfaces.application.BackwardsCompatibleNavigationHandlerWrapper;
import org.apache.myfaces.component.visit.VisitContextFactoryImpl;
import org.apache.myfaces.config.element.Behavior;
import org.apache.myfaces.config.element.ClientBehaviorRenderer;
import org.apache.myfaces.config.element.ComponentTagDeclaration;
import org.apache.myfaces.config.element.ContractMapping;
import org.apache.myfaces.config.element.FaceletsProcessing;
import org.apache.myfaces.config.element.FacesConfig;
import org.apache.myfaces.config.element.FacesConfigData;
import org.apache.myfaces.config.element.FacesFlowCall;
import org.apache.myfaces.config.element.FacesFlowDefinition;
import org.apache.myfaces.config.element.FacesFlowMethodCall;
import org.apache.myfaces.config.element.FacesFlowMethodParameter;
import org.apache.myfaces.config.element.FacesFlowParameter;
import org.apache.myfaces.config.element.FacesFlowReturn;
import org.apache.myfaces.config.element.FacesFlowSwitch;
import org.apache.myfaces.config.element.FacesFlowView;
import org.apache.myfaces.config.element.NamedEvent;
import org.apache.myfaces.config.element.NavigationCase;
import org.apache.myfaces.config.element.NavigationRule;
import org.apache.myfaces.config.element.Renderer;
import org.apache.myfaces.config.element.ResourceBundle;
import org.apache.myfaces.config.element.SystemEventListener;
import org.apache.myfaces.config.impl.FacesConfigDispenserImpl;
import org.apache.myfaces.context.ExceptionHandlerFactoryImpl;
import org.apache.myfaces.context.ExternalContextFactoryImpl;
import org.apache.myfaces.context.FacesContextFactoryImpl;
import org.apache.myfaces.context.PartialViewContextFactoryImpl;
import org.apache.myfaces.context.servlet.ServletFlashFactoryImpl;
import org.apache.myfaces.lifecycle.clientwindow.ClientWindowFactoryImpl;
import org.apache.myfaces.flow.FlowCallNodeImpl;
import org.apache.myfaces.flow.FlowHandlerFactoryImpl;
import org.apache.myfaces.flow.FlowImpl;
import org.apache.myfaces.flow.MethodCallNodeImpl;
import org.apache.myfaces.flow.ParameterImpl;
import org.apache.myfaces.flow.ReturnNodeImpl;
import org.apache.myfaces.flow.SwitchCaseImpl;
import org.apache.myfaces.flow.SwitchNodeImpl;
import org.apache.myfaces.flow.ViewNodeImpl;
import org.apache.myfaces.lifecycle.LifecycleFactoryImpl;
import org.apache.myfaces.renderkit.RenderKitFactoryImpl;
import org.apache.myfaces.renderkit.html.HtmlRenderKitImpl;
import org.apache.myfaces.resource.ResourceLoaderUtils;
import org.apache.myfaces.util.lang.ClassUtils;
import org.apache.myfaces.util.LocaleUtils;
import org.apache.myfaces.application.viewstate.StateUtils;
import org.apache.myfaces.util.lang.StringUtils;
import org.apache.myfaces.util.WebConfigParamUtils;
import org.apache.myfaces.cdi.util.BeanEntry;
import org.apache.myfaces.cdi.view.ViewScopeEventListener;
import org.apache.myfaces.component.search.SearchExpressionContextFactoryImpl;
import org.apache.myfaces.config.element.Component;
import org.apache.myfaces.config.element.FaceletsTemplateMapping;
import org.apache.myfaces.config.element.ViewPoolMapping;
import org.apache.myfaces.config.element.facelets.FaceletTagLibrary;
import org.apache.myfaces.config.impl.FacesConfigUnmarshallerImpl;
import org.apache.myfaces.config.webparameters.MyfacesConfig;
import org.apache.myfaces.el.TracingELResolverPhaseListener;
import org.apache.myfaces.lifecycle.LifecycleImpl;
import org.apache.myfaces.renderkit.LazyRenderKit;
import org.apache.myfaces.spi.impl.DefaultSerialFactory;
import org.apache.myfaces.spi.SerialFactory;
import org.apache.myfaces.spi.FacesConfigurationMerger;
import org.apache.myfaces.spi.FacesConfigurationMergerFactory;
import org.apache.myfaces.spi.FacesFlowProvider;
import org.apache.myfaces.spi.FacesFlowProviderFactory;
import org.apache.myfaces.spi.InjectionProvider;
import org.apache.myfaces.spi.InjectionProviderException;
import org.apache.myfaces.spi.InjectionProviderFactory;
import org.apache.myfaces.spi.ResourceLibraryContractsProvider;
import org.apache.myfaces.spi.ResourceLibraryContractsProviderFactory;
import org.apache.myfaces.util.ExternalSpecifications;
import org.apache.myfaces.util.NavigationUtils;
import org.apache.myfaces.util.Purgeable;
import org.apache.myfaces.view.ViewDeclarationLanguageFactoryImpl;
import org.apache.myfaces.view.facelets.el.ELText;
import org.apache.myfaces.view.facelets.impl.FaceletCacheFactoryImpl;
import org.apache.myfaces.view.facelets.tag.faces.TagHandlerDelegateFactoryImpl;
import org.apache.myfaces.view.facelets.tag.ui.DebugPhaseListener;
import org.apache.myfaces.webapp.FacesInitializerImpl;

/**
 * Configures everything for a given context. The FacesConfigurator is independent of the concrete implementations that
 * lie behind FacesConfigUnmarshaller and FacesConfigDispenser.
 *
 * @author Manfred Geiler (latest modification by $Author$)
 * @version $Revision$ $Date$
 */
@SuppressWarnings("deprecation")
public class FacesConfigurator
{
    private final Class<?>[] NO_PARAMETER_TYPES = new Class[]{};
    private final Object[] NO_PARAMETERS = new Object[]{};

    private static final Logger log = Logger.getLogger(FacesConfigurator.class.getName());

    private static final String DEFAULT_RENDER_KIT_CLASS = HtmlRenderKitImpl.class.getName();
    private static final String DEFAULT_APPLICATION_FACTORY = ApplicationFactoryImpl.class.getName();
    private static final String DEFAULT_EXTERNAL_CONTEXT_FACTORY = ExternalContextFactoryImpl.class.getName();
    private static final String DEFAULT_FACES_CONTEXT_FACTORY = FacesContextFactoryImpl.class.getName();
    private static final String DEFAULT_LIFECYCLE_FACTORY = LifecycleFactoryImpl.class.getName();
    private static final String DEFAULT_RENDER_KIT_FACTORY = RenderKitFactoryImpl.class.getName();
    private static final String DEFAULT_PARTIAL_VIEW_CONTEXT_FACTORY = PartialViewContextFactoryImpl.class.getName();
    private static final String DEFAULT_VISIT_CONTEXT_FACTORY = VisitContextFactoryImpl.class.getName();
    private static final String DEFAULT_VIEW_DECLARATION_LANGUAGE_FACTORY
            = ViewDeclarationLanguageFactoryImpl.class.getName();
    private static final String DEFAULT_EXCEPTION_HANDLER_FACTORY = ExceptionHandlerFactoryImpl.class.getName();
    private static final String DEFAULT_TAG_HANDLER_DELEGATE_FACTORY = TagHandlerDelegateFactoryImpl.class.getName();
    private static final String DEFAULT_FACELET_CACHE_FACTORY = FaceletCacheFactoryImpl.class.getName();
    private static final String DEFAULT_FLASH_FACTORY = ServletFlashFactoryImpl.class.getName();
    private static final String DEFAULT_CLIENT_WINDOW_FACTORY = ClientWindowFactoryImpl.class.getName();
    private static final String DEFAULT_FLOW_FACTORY = FlowHandlerFactoryImpl.class.getName();
    private static final String DEFAULT_SEARCH_EXPRESSION_CONTEXT_FACTORY = 
            SearchExpressionContextFactoryImpl.class.getName();
    private static final String DEFAULT_FACES_CONFIG = "/WEB-INF/faces-config.xml";

    /**
     * Set this attribute if the current configuration requires enable window mode
     */
    public static final String ENABLE_DEFAULT_WINDOW_MODE = 
        "org.apache.myfaces.ENABLE_DEFAULT_WINDOW_MODE";

    private final ExternalContext _externalContext;
    private FacesContext _facesContext;
    private FacesConfigUnmarshaller<? extends FacesConfig> _unmarshaller;
    private FacesConfigData _dispenser;

    private RuntimeConfig _runtimeConfig;
    private MyfacesConfig _myfacesConfig;
    
    private Application _application;
    
    private InjectionProvider _injectionProvider;

    private static long lastUpdate;

    public FacesConfigurator(ExternalContext externalContext)
    {
        if (externalContext == null)
        {
            throw new IllegalArgumentException("external context must not be null");
        }
        _externalContext = externalContext;

        // In dev mode a new Faces Configurator is created for every request so only
        // create a new bean storage list if we don't already have one which will be
        // the case first time through during init.
        if (_externalContext.getApplicationMap().get(FacesInitializerImpl.INJECTED_BEAN_STORAGE_KEY) == null)
        {
            _externalContext.getApplicationMap().put(FacesInitializerImpl.INJECTED_BEAN_STORAGE_KEY,
                    new CopyOnWriteArrayList());
        }
        
        _myfacesConfig = MyfacesConfig.getCurrentInstance(_externalContext);
        _runtimeConfig = RuntimeConfig.getCurrentInstance(_externalContext);
    }

    /**
     * @param unmarshaller
     *            the unmarshaller to set
     */
    public void setUnmarshaller(FacesConfigUnmarshaller<? extends FacesConfig> unmarshaller)
    {
        _unmarshaller = unmarshaller;
    }

    /**
     * @return the unmarshaller
     */
    protected FacesConfigUnmarshaller<? extends FacesConfig> getUnmarshaller()
    {
        if (_unmarshaller == null)
        {
            _unmarshaller = new FacesConfigUnmarshallerImpl(_externalContext);
        }

        return _unmarshaller;
    }

    /**
     * @param dispenser
     *            the dispenser to set
     */
    public void setDispenser(FacesConfigData dispenser)
    {
        _dispenser = dispenser;
    }

    /**
     * @return the dispenser
     */
    protected FacesConfigData getDispenser()
    {
        if (_dispenser == null)
        {
            _dispenser = new FacesConfigDispenserImpl();
        }

        return _dispenser;
    }

    private long getResourceLastModified(String resource)
    {
        try
        {
            URL url = _externalContext.getResource(resource);
            if (url != null)
            {
                return ResourceLoaderUtils.getResourceLastModified(url);
            }
        }
        catch (IOException e)
        {
            log.log(Level.SEVERE, "Could not read lastModified " + resource, e);
        }
        return 0;
    }

    private long getLastModifiedTime()
    {
        long lastModified = 0;
        long resModified;

        resModified = getResourceLastModified(DEFAULT_FACES_CONFIG);
        if (resModified > lastModified)
        {
            lastModified = resModified;
        }

        // perf: method getConfigFilesList() creates a ArrayList    
        List<String> configFilesList = getConfigFilesList();
        for (int i = 0, size = configFilesList.size(); i < size; i++)
        {
            String systemId = configFilesList.get(i);
            resModified = getResourceLastModified(systemId);
            if (resModified > lastModified)
            {
                lastModified = resModified;
            }
        }
        
        // get last modified from .taglib.xml
        String faceletsFiles = WebConfigParamUtils.getStringInitParameter(_externalContext,
                ViewHandler.FACELETS_LIBRARIES_PARAM_NAME);
        if (faceletsFiles != null)
        {
            String[] faceletFilesList = StringUtils.trim(faceletsFiles.split(";"));
            for (int i = 0, size = faceletFilesList.length; i < size; i++)
            {
                String systemId = faceletFilesList[i];
                resModified = getResourceLastModified(systemId);
                if (resModified > lastModified)
                {
                    lastModified = resModified;
                }
            }
        }
        
        // get last modified from -flow.xml
        Set<String> directoryPaths = _externalContext.getResourcePaths("/");
        if (directoryPaths != null)
        {
            List<String> contextSpecifiedList = configFilesList;
            for (String dirPath : directoryPaths)
            {
                if (dirPath.equals("/WEB-INF/"))
                {
                    // Look on /WEB-INF/<flow-Name>/<flowName>-flow.xml
                    Set<String> webDirectoryPaths = _externalContext.getResourcePaths(dirPath);
                    for (String webDirPath : webDirectoryPaths)
                    {
                        if (webDirPath.endsWith("/") && 
                            !webDirPath.equals("/WEB-INF/classes/"))
                        {
                            String flowName = webDirPath.substring(9, webDirPath.length() - 1);
                            String filePath = webDirPath+flowName+"-flow.xml";
                            if (!contextSpecifiedList.contains(filePath))
                            {
                                resModified = getResourceLastModified(filePath);
                                if (resModified > lastModified)
                                {
                                    lastModified = resModified;
                                }
                            }
                        }
                    }
                }
                else if (!dirPath.startsWith("/META-INF") && dirPath.endsWith("/"))
                {
                    // Look on /<flowName>/<flowName>-flow.xml
                    String flowName = dirPath.substring(1, dirPath.length() - 1);
                    String filePath = dirPath+flowName+"-flow.xml";
                    if (!contextSpecifiedList.contains(filePath))
                    {
                        resModified = getResourceLastModified(filePath);
                        if (resModified > lastModified)
                        {
                            lastModified = resModified;
                        }
                    }
                }                
            }
        }
        
        return lastModified;
    }

    public void update()
    {
        long refreshPeriod = (MyfacesConfig.getCurrentInstance(_externalContext).getConfigRefreshPeriod()) * 1000;
        if (refreshPeriod > 0)
        {
            long ttl = lastUpdate + refreshPeriod;
            if ((System.currentTimeMillis() > ttl) && (getLastModifiedTime() > ttl))
            {
                try
                {
                    purgeConfiguration();
  
                    configure();

                    // Faces 2.0 Publish PostConstructApplicationEvent after all configuration resources
                    // has been parsed and processed
                    FacesContext facesContext = getFacesContext();
                    Application application = facesContext.getApplication();

                    application.publishEvent(facesContext, PostConstructApplicationEvent.class,
                            Application.class, application);
                }
                catch (Exception e)
                {
                    log.severe("Error during configuration clean-up" + e.getMessage());

                    // We still want to update the timestamp to avoid running purge on every subsequent
                    // request after this one.
                    //
                    lastUpdate = System.currentTimeMillis();
                }
            }
        }
    }

    private void purgeConfiguration()
    {
        // Check that we have access to all of the necessary purge methods before purging anything

        ApplicationFactory applicationFactory
                = (ApplicationFactory) FactoryFinder.getFactory(FactoryFinder.APPLICATION_FACTORY);
        purge(applicationFactory);

        RenderKitFactory renderKitFactory
                = (RenderKitFactory) FactoryFinder.getFactory(FactoryFinder.RENDER_KIT_FACTORY);
        purge(renderKitFactory);

        RuntimeConfig.getCurrentInstance(_externalContext).purge();

        LifecycleFactory lifecycleFactory
                = (LifecycleFactory) FactoryFinder.getFactory(FactoryFinder.LIFECYCLE_FACTORY);
        purge(lifecycleFactory);

        FacesContext facesContext = getFacesContext();
        purge(facesContext);

        _externalContext.getApplicationMap().remove(LifecycleImpl.FIRST_REQUEST_PROCESSED_PARAM);
    }
    
    <T> void purge(T instance)
    {        
        while (instance != null)
        {
            if (instance instanceof Purgeable)
            {
                ((Purgeable) instance).purge();
            }

            if (instance instanceof FacesWrapper)
            {
                instance = ((FacesWrapper<T>) instance).getWrapped();
            }
            else
            {
                return;
            }
        }
    }

    public void configure() throws FacesException
    {
        // get FacesConfigurationMerger SPI implementation
        FacesConfigurationMerger facesConfigurationMerger = FacesConfigurationMergerFactory
                .getFacesConfigurationMergerFactory(_externalContext).getFacesConfigurationMerger(_externalContext);

        // get all faces-config data, merge it and set it as Dispenser
        setDispenser(facesConfigurationMerger.getFacesConfigData(_externalContext));

        configureFactories();
        configureApplication();
        configureRenderKits();

        configureRuntimeConfig();
        configureLifecycle();
        handleSerialFactory();
        configureFlowHandler();

        configureProtectedViews();
        
        // record the time of update
        lastUpdate = System.currentTimeMillis();
    }

    private List<String> getConfigFilesList()
    {
        String configFiles = _externalContext.getInitParameter(FacesServlet.CONFIG_FILES_ATTR);
        List<String> configFilesList = new ArrayList<>();
        if (configFiles != null)
        {
            StringTokenizer st = new StringTokenizer(configFiles, ",", false);
            while (st.hasMoreTokens())
            {
                String systemId = st.nextToken().trim();

                if (DEFAULT_FACES_CONFIG.equals(systemId))
                {
                    if (log.isLoggable(Level.WARNING))
                    {
                        log.warning(DEFAULT_FACES_CONFIG + " has been specified in the "
                                + FacesServlet.CONFIG_FILES_ATTR
                                + " context parameter of "
                                + "the deployment descriptor. This will automatically be removed, "
                                + "if we wouldn't do this, it would be loaded twice.  See Faces spec 1.1, 10.3.2");
                    }
                }
                else
                {
                    configFilesList.add(systemId);
                }
            }
        }
        return configFilesList;
    }

    private void configureFactories()
    {
        FacesConfigData dispenser = getDispenser();
        setFactories(FactoryFinder.APPLICATION_FACTORY, dispenser.getApplicationFactoryIterator(),
                DEFAULT_APPLICATION_FACTORY);
        setFactories(FactoryFinder.EXCEPTION_HANDLER_FACTORY, dispenser.getExceptionHandlerFactoryIterator(),
                DEFAULT_EXCEPTION_HANDLER_FACTORY);
        setFactories(FactoryFinder.EXTERNAL_CONTEXT_FACTORY, dispenser.getExternalContextFactoryIterator(),
                DEFAULT_EXTERNAL_CONTEXT_FACTORY);
        setFactories(FactoryFinder.FACES_CONTEXT_FACTORY, dispenser.getFacesContextFactoryIterator(),
                DEFAULT_FACES_CONTEXT_FACTORY);
        setFactories(FactoryFinder.LIFECYCLE_FACTORY, dispenser.getLifecycleFactoryIterator(),
                DEFAULT_LIFECYCLE_FACTORY);
        setFactories(FactoryFinder.RENDER_KIT_FACTORY, dispenser.getRenderKitFactoryIterator(),
                DEFAULT_RENDER_KIT_FACTORY);
        setFactories(FactoryFinder.TAG_HANDLER_DELEGATE_FACTORY, dispenser.getTagHandlerDelegateFactoryIterator(),
                DEFAULT_TAG_HANDLER_DELEGATE_FACTORY);
        setFactories(FactoryFinder.PARTIAL_VIEW_CONTEXT_FACTORY, dispenser.getPartialViewContextFactoryIterator(),
                DEFAULT_PARTIAL_VIEW_CONTEXT_FACTORY);
        setFactories(FactoryFinder.VISIT_CONTEXT_FACTORY, dispenser.getVisitContextFactoryIterator(),
                DEFAULT_VISIT_CONTEXT_FACTORY);
        setFactories(FactoryFinder.VIEW_DECLARATION_LANGUAGE_FACTORY,
                dispenser.getViewDeclarationLanguageFactoryIterator(),
                DEFAULT_VIEW_DECLARATION_LANGUAGE_FACTORY);
        setFactories(FactoryFinder.FACELET_CACHE_FACTORY, dispenser.getFaceletCacheFactoryIterator(),
                DEFAULT_FACELET_CACHE_FACTORY);
        setFactories(FactoryFinder.FLASH_FACTORY, dispenser.getFlashFactoryIterator(),
                DEFAULT_FLASH_FACTORY);
        setFactories(FactoryFinder.CLIENT_WINDOW_FACTORY, dispenser.getClientWindowFactoryIterator(),
                DEFAULT_CLIENT_WINDOW_FACTORY);
        setFactories(FactoryFinder.FLOW_HANDLER_FACTORY, dispenser.getFlowHandlerFactoryIterator(),
                DEFAULT_FLOW_FACTORY);
        setFactories(FactoryFinder.SEARCH_EXPRESSION_CONTEXT_FACTORY, 
                dispenser.getSearchExpressionContextFactoryIterator(),
                DEFAULT_SEARCH_EXPRESSION_CONTEXT_FACTORY);
    }

    private void setFactories(String factoryName, Collection<String> factories, String defaultFactory)
    {
        FactoryFinder.setFactory(factoryName, defaultFactory);
        for (String factory : factories)
        {
            if (!factory.equals(defaultFactory))
            {
                FactoryFinder.setFactory(factoryName, factory);
            }
        }
    }

    private void configureApplication()
    {
        Application application = ((ApplicationFactory)
                FactoryFinder.getFactory(FactoryFinder.APPLICATION_FACTORY)).getApplication();

        FacesConfigData dispenser = getDispenser();
        ActionListener actionListener = ClassUtils.buildApplicationObject(ActionListener.class,
                dispenser.getActionListenerIterator(), null);
        _callInjectAndPostConstruct(actionListener);
        application.setActionListener(actionListener);

        if (dispenser.getDefaultLocale() != null)
        {
            application.setDefaultLocale(LocaleUtils.toLocale(dispenser.getDefaultLocale()));
        }

        if (dispenser.getDefaultRenderKitId() != null)
        {
            application.setDefaultRenderKitId(dispenser.getDefaultRenderKitId());
        }

        if (dispenser.getMessageBundle() != null)
        {
            application.setMessageBundle(dispenser.getMessageBundle());
        }
        
        // First build the object
        NavigationHandler navigationHandler = ClassUtils.buildApplicationObject(NavigationHandler.class,
                ConfigurableNavigationHandler.class, null,
                dispenser.getNavigationHandlerIterator(),
                application.getNavigationHandler());
        // Invoke inject and post construct
        _callInjectAndPostConstruct(navigationHandler);
        // Finally wrap the object with the BackwardsCompatibleNavigationHandlerWrapper if it is not assignable
        // from ConfigurableNavigationHandler
        navigationHandler = ClassUtils.wrapBackwardCompatible(NavigationHandler.class,
                ConfigurableNavigationHandler.class,
                BackwardsCompatibleNavigationHandlerWrapper.class,
                application.getNavigationHandler(),
                navigationHandler);
        application.setNavigationHandler(navigationHandler);

        StateManager stateManager = ClassUtils.buildApplicationObject(StateManager.class,
                dispenser.getStateManagerIterator(),
                application.getStateManager());
        _callInjectAndPostConstruct(stateManager);
        application.setStateManager(stateManager);

        ResourceHandler resourceHandler = ClassUtils.buildApplicationObject(ResourceHandler.class,
                dispenser.getResourceHandlerIterator(),
                application.getResourceHandler());
        _callInjectAndPostConstruct(resourceHandler);
        application.setResourceHandler(resourceHandler);

        List<Locale> locales = new ArrayList<>();
        for (String locale : dispenser.getSupportedLocalesIterator())
        {
            locales.add(LocaleUtils.toLocale(locale));
        }

        application.setSupportedLocales(locales);

        application.setViewHandler(ClassUtils.buildApplicationObject(ViewHandler.class,
                dispenser.getViewHandlerIterator(),
                application.getViewHandler()));
        
        application.setSearchExpressionHandler(ClassUtils.buildApplicationObject(SearchExpressionHandler.class,
                dispenser.getSearchExpressionHandlerIterator(),
                application.getSearchExpressionHandler()));
                
        for (SystemEventListener systemEventListener : dispenser.getSystemEventListeners())
        {
            try
            {
                //note here used to be an instantiation to deal with the explicit source type in the registration,
                // that cannot work because all system events need to have the source being passed in the constructor
                //instead we now  rely on the standard system event types and map them to their appropriate
                // constructor types
                Class eventClass = ClassUtils.classForName((systemEventListener.getSystemEventClass() != null)
                        ? systemEventListener.getSystemEventClass()
                        : SystemEvent.class.getName());

                jakarta.faces.event.SystemEventListener listener = (jakarta.faces.event.SystemEventListener) 
                        ClassUtils.newInstance(systemEventListener.getSystemEventListenerClass());
                _callInjectAndPostConstruct(listener);
                if (systemEventListener.getSourceClass() != null && systemEventListener.getSourceClass().length() > 0)
                {
                    application.subscribeToEvent(
                            (Class<? extends SystemEvent>) eventClass,
                            ClassUtils.classForName(systemEventListener.getSourceClass()),
                                    listener);
                }
                else
                {
                    application.subscribeToEvent((Class<? extends SystemEvent>) eventClass, listener);
                }
            }
            catch (ClassNotFoundException e)
            {
                log.log(Level.SEVERE, "System event listener could not be initialized, reason:", e);
            }
        }
        
        ViewScopeEventListener viewScopeEventListener = new ViewScopeEventListener();
        application.subscribeToEvent(PostConstructViewMapEvent.class, UIViewRoot.class, viewScopeEventListener);
        application.subscribeToEvent(PreDestroyViewMapEvent.class, UIViewRoot.class, viewScopeEventListener);

        for (Map.Entry<String, Component> entry : dispenser.getComponentsByType().entrySet())
        {
            application.addComponent(entry.getKey(), entry.getValue().getComponentClass());
        }

        for (Map.Entry<String, String> entry : dispenser.getConverterClassesById().entrySet())
        {
            application.addConverter(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, String> entry : dispenser.getConverterClassesByClass().entrySet())
        {
            try
            {
                application.addConverter(ClassUtils.classForName(entry.getKey()),
                        entry.getValue());
            }
            catch (Exception ex)
            {
                log.log(Level.SEVERE, "Converter could not be added. Reason:", ex);
            }
        }

        for (Map.Entry<String, String> entry : dispenser.getValidatorClassesById().entrySet())
        {
            application.addValidator(entry.getKey(), entry.getValue());
        }

        // programmatically add the BeanValidator if the following requirements are met:
        //     - bean validation has not been disabled
        //     - bean validation is available in the classpath
        String beanValidatorDisabled = _externalContext.getInitParameter(
                BeanValidator.DISABLE_DEFAULT_BEAN_VALIDATOR_PARAM_NAME);
        final boolean defaultBeanValidatorDisabled = (beanValidatorDisabled != null
                && beanValidatorDisabled.equalsIgnoreCase("true"));
        boolean beanValidatorInstalledProgrammatically = false;
        if (!defaultBeanValidatorDisabled
                && ExternalSpecifications.isBeanValidationAvailable())
        {
            // add the BeanValidator as default validator
            application.addDefaultValidatorId(BeanValidator.VALIDATOR_ID);
            beanValidatorInstalledProgrammatically = true;
        }

        // add the default-validators from the config files
        for (String validatorId : dispenser.getDefaultValidatorIds())
        {
            application.addDefaultValidatorId(validatorId);
        }

        // do some checks if the BeanValidator was not installed as a
        // default-validator programmatically, but via a config file.
        if (!beanValidatorInstalledProgrammatically
                && application.getDefaultValidatorInfo()
                .containsKey(BeanValidator.VALIDATOR_ID))
        {
            if (!ExternalSpecifications.isBeanValidationAvailable())
            {
                // the BeanValidator was installed via a config file,
                // but bean validation is not available
                log.log(Level.WARNING, "The BeanValidator was installed as a " +
                        "default-validator from a faces-config file, but bean " +
                        "validation is not available on the classpath, " +
                        "thus it will not work!");
            }
            else if (defaultBeanValidatorDisabled)
            {
                // the user disabled the default bean validator in web.xml,
                // but a config file added it, which is ok with the spec
                // (section 11.1.3: "though manual installation is still possible")
                // --> inform the user about this scenario
                log.log(Level.INFO, "The BeanValidator was disabled as a " +
                        "default-validator via the config parameter " +
                        BeanValidator.DISABLE_DEFAULT_BEAN_VALIDATOR_PARAM_NAME +
                        " in web.xml, but a faces-config file added it, " +
                        "thus it actually was installed as a default-validator.");
            }
        }

        for (Behavior behavior : dispenser.getBehaviors())
        {
            application.addBehavior(behavior.getBehaviorId(), behavior.getBehaviorClass());
        }
        
        //Faces 2.2 set FlowHandler from factory. 
        FlowHandlerFactory flowHandlerFactory = (FlowHandlerFactory) 
            FactoryFinder.getFactory(FactoryFinder.FLOW_HANDLER_FACTORY);
        FlowHandler flowHandler = flowHandlerFactory.createFlowHandler(
            getFacesContext());
        application.setFlowHandler(flowHandler);
        
        for (ContractMapping mapping : dispenser.getResourceLibraryContractMappings())
        {
            List<String> urlMappingsList = mapping.getUrlPatternList();
            for (String urlPattern: urlMappingsList)
            {
                for (String contract : mapping.getContractList())
                {
                    String[] contracts = StringUtils.trim(StringUtils.splitShortString(contract, ' '));
                    _runtimeConfig.addContractMapping(urlPattern, contracts);
                }
            }
        }
        
        this.setApplication(application);
    }

    private void _callInjectAndPostConstruct(Object instance)
    {
        try
        {
            //invoke the injection over the inner one first
            if (instance instanceof FacesWrapper)
            {
                Object innerInstance = ((FacesWrapper) instance).getWrapped();
                if (innerInstance != null)
                {
                    _callInjectAndPostConstruct(innerInstance);
                }
            }
            List<BeanEntry> injectedBeanStorage = (List<BeanEntry>) _externalContext.getApplicationMap()
                    .get(FacesInitializerImpl.INJECTED_BEAN_STORAGE_KEY);

            Object creationMetaData = getInjectionProvider().inject(instance);

            injectedBeanStorage.add(new BeanEntry(instance, creationMetaData));

            getInjectionProvider().postConstruct(instance, creationMetaData);
        }
        catch (InjectionProviderException ex)
        {
            log.log(Level.INFO, "Exception on Injection or PostConstruct", ex);
        }
    }

    private void configureRuntimeConfig()
    {
        RuntimeConfig runtimeConfig = RuntimeConfig.getCurrentInstance(_externalContext);
        FacesConfigData dispenser = getDispenser();
        List<String> knownNamespaces = new ArrayList<>();
        
        for (ComponentTagDeclaration declaration : dispenser.getComponentTagDeclarations())
        {
            runtimeConfig.addComponentTagDeclaration(declaration);
            if (declaration.getNamespace() != null)
            {
                knownNamespaces.add(declaration.getNamespace());
            }
        }

        for (NavigationRule rule : dispenser.getNavigationRules())
        {
            runtimeConfig.addNavigationRule(rule);
        }

        for (String converterClassName : dispenser.getConverterConfigurationByClassName())
        {
            runtimeConfig.addConverterConfiguration(converterClassName,
                    _dispenser.getConverterConfiguration(converterClassName));
        }

        for (ResourceBundle bundle : dispenser.getResourceBundles())
        {
            runtimeConfig.addResourceBundle(bundle);
        }

        for (String className : dispenser.getElResolvers())
        {
            ELResolver elResolver = (ELResolver) ClassUtils.newInstance(className, ELResolver.class);
            _callInjectAndPostConstruct(elResolver);
            runtimeConfig.addFacesConfigElResolver(elResolver);
        }
        
        for (String className : dispenser.getSearchKeywordResolvers())
        {
            SearchKeywordResolver searchKeywordResolver = (SearchKeywordResolver) ClassUtils.newInstance(
                    className, SearchKeywordResolver.class);
            
            runtimeConfig.addApplicationSearchExpressionResolver(searchKeywordResolver);
        }

        runtimeConfig.setFacesVersion(dispenser.getFacesVersion());

        runtimeConfig.setNamedEventManager(new NamedEventManager());

        for (NamedEvent event : dispenser.getNamedEvents())
        {
            try
            {
                Class<? extends ComponentSystemEvent> clazz = ClassUtils.classForName(event.getEventClass());
                runtimeConfig.getNamedEventManager().addNamedEvent(event.getShortName(), clazz);
            }
            catch (ClassNotFoundException e)
            {
                log.log(Level.SEVERE, "Named event could not be initialized, reason:", e);
            }
        }

        String elResolverComparatorClass = _myfacesConfig.getElResolverComparator();
        if (elResolverComparatorClass != null && !elResolverComparatorClass.isEmpty())
        {
            try
            {
                Class<Comparator<ELResolver>> clazz = ClassUtils.classForName(elResolverComparatorClass);
                Comparator<ELResolver> comparator = ClassUtils.newInstance(clazz);
                runtimeConfig.setELResolverComparator(comparator);
            }
            catch (Exception e)
            {
                if (log.isLoggable(Level.SEVERE))
                {
                    log.log(Level.SEVERE, "Cannot instantiate EL Resolver Comparator " + elResolverComparatorClass
                            + " . Check " + MyfacesConfig.EL_RESOLVER_COMPARATOR + " web config param. "
                            + "Initialization continues with no comparator used.", e);
                }
            }
        }
        else
        {
            runtimeConfig.setELResolverComparator(null);
        }

        String elResolverPredicateClass = _myfacesConfig.getElResolverPredicate();
        if (elResolverPredicateClass != null && !elResolverPredicateClass.isEmpty())
        {
            try
            {
                Class<?> clazz = ClassUtils.classForName(elResolverPredicateClass);
                Object elResolverPredicate = ClassUtils.newInstance(clazz);
                if (elResolverPredicate instanceof Predicate)
                {
                    runtimeConfig.setELResolverPredicate((Predicate) elResolverPredicate);
                }
                else
                {
                    if (log.isLoggable(Level.SEVERE))
                    {
                        log.log(Level.SEVERE, "EL Resolver Predicate " + elResolverPredicateClass
                                + " must implement " + Predicate.class.getName()
                                + " . Check " + MyfacesConfig.EL_RESOLVER_PREDICATE + " web config param. "
                                + "Initialization continues with no predicate used.");
                    }
                }
            }
            catch (Exception e)
            {
                if (log.isLoggable(Level.SEVERE))
                {
                    log.log(Level.SEVERE, "Cannot instantiate EL Resolver Predicate " + elResolverPredicateClass
                            + " . Check " + MyfacesConfig.EL_RESOLVER_PREDICATE + " web config param. "
                            + "Initialization continues with no predicate used.", e);
                }
            }
        }
        else
        {
            runtimeConfig.setELResolverPredicate(null);
        }

        for (FaceletsProcessing faceletsProcessing : dispenser.getFaceletsProcessing())
        {
            runtimeConfig.addFaceletProcessingConfiguration(faceletsProcessing.getFileExtension(), faceletsProcessing);
        }
        
        ResourceLibraryContractsProvider rlcp = ResourceLibraryContractsProviderFactory.
            getFacesConfigResourceProviderFactory(_externalContext).
            createResourceLibraryContractsProvider(_externalContext);
        
        try
        {
            // Faces 2.2 section 11.4.2.1 scan for available resource library contracts
            // and store the result in a internal data structure, so it can be used 
            // later in ViewDeclarationLanguage.calculateResourceLibraryContracts(
            //   FacesContext context, String viewId)
            runtimeConfig.setExternalContextResourceLibraryContracts(
                rlcp.getExternalContextResourceLibraryContracts(_externalContext));
            runtimeConfig.setClassLoaderResourceLibraryContracts(
                rlcp.getClassloaderResourceLibraryContracts(_externalContext));
        }
        catch (Exception e)
        {
            if (log.isLoggable(Level.SEVERE))
            {
                log.log(Level.SEVERE, 
                    "An error was found when scanning for resource library contracts", e);
            }
        }
        
        
        // Faces 2.2 section 11.4.2.1 check all contracts are loaded
        if (log.isLoggable(Level.INFO))
        {
            for (List<String> list : runtimeConfig.getContractMappings().values())
            {
                for (String contract : list)
                {
                    if (!runtimeConfig.getResourceLibraryContracts().contains(contract))
                    {
                        log.log(Level.INFO, 
                            "Resource Library Contract "+ contract + " was not found while scanning for "
                            + "available contracts.");
                    }
                }
            }
        }
        
        // Faces 2.2 section 11.4.2.1 if no contractMappings set, all available contracts applies
        // to all views.
        if (runtimeConfig.getContractMappings().isEmpty())
        {
            String[] contracts = runtimeConfig.getResourceLibraryContracts().toArray(
                new String[runtimeConfig.getResourceLibraryContracts().size()]);
            runtimeConfig.addContractMapping("*", contracts);
        }
        
        for (String resourceResolver : dispenser.getResourceResolvers())
        {
            runtimeConfig.addResourceResolver(resourceResolver);
        }
        
        for (FaceletTagLibrary faceletTagLibrary : dispenser.getTagLibraries())
        {
            runtimeConfig.addFaceletTagLibrary(faceletTagLibrary);
            if (faceletTagLibrary.getNamespace() != null)
            {
                knownNamespaces.add(faceletTagLibrary.getNamespace());
            }
        }
        
        // Add default namespaces to the known namespaces
        knownNamespaces.add("http://xmlns.jcp.org/jsf/core");
        knownNamespaces.add("http://java.sun.com/jsf/core");
        knownNamespaces.add("http://xmlns.jcp.org/jsf/html");
        knownNamespaces.add("http://java.sun.com/jsf/html");
        knownNamespaces.add("http://xmlns.jcp.org/jsf/facelets");
        knownNamespaces.add("http://java.sun.com/jsf/facelets");
        knownNamespaces.add("http://xmlns.jcp.org/jsp/jstl/core");
        knownNamespaces.add("http://java.sun.com/jsp/jstl/core");
        knownNamespaces.add("http://java.sun.com/jstl/core");
        knownNamespaces.add("http://xmlns.jcp.org/jsp/jstl/functions");
        knownNamespaces.add("http://java.sun.com/jsp/jstl/functions");
        knownNamespaces.add("http://xmlns.jcp.org/jsf/composite");
        knownNamespaces.add("http://java.sun.com/jsf/composite");
        knownNamespaces.add("http://xmlns.jcp.org/jsf");
        knownNamespaces.add("http://java.sun.com/jsf");
        knownNamespaces.add("http://xmlns.jcp.org/jsf/passthrough");
        knownNamespaces.add("http://java.sun.com/jsf/passthrough");
        
        Map<Integer, String> namespaceById = new HashMap<>();
        Map<String, Integer> idByNamespace = new HashMap<>();
        // Sort them to ensure the same id 
        Collections.sort(knownNamespaces);
        for (int i = 0; i < knownNamespaces.size(); i++)
        {
            namespaceById.put(i, knownNamespaces.get(i));
            idByNamespace.put(knownNamespaces.get(i), i);
        }
        runtimeConfig.setNamespaceById(Collections.unmodifiableMap(namespaceById));
        runtimeConfig.setIdByNamespace(Collections.unmodifiableMap(idByNamespace));
        
        for (ViewPoolMapping viewPoolMapping : dispenser.getViewPoolMappings())
        {
            runtimeConfig.addViewPoolMapping(viewPoolMapping);
        }
        
        for (FaceletsTemplateMapping faceletsTemplateMapping : dispenser.getFaceletsTemplateMappings())
        {
            runtimeConfig.addFaceletsTemplateMapping(faceletsTemplateMapping);
        }
    }

    private void configureRenderKits()
    {
        RenderKitFactory renderKitFactory
                = (RenderKitFactory) FactoryFinder.getFactory(FactoryFinder.RENDER_KIT_FACTORY);

        FacesConfigData dispenser = getDispenser();
        for (String renderKitId : dispenser.getRenderKitIds())
        {
            Collection<String> renderKitClass = dispenser.getRenderKitClasses(renderKitId);

            if (renderKitClass.isEmpty())
            {
                renderKitClass = new ArrayList<>(1);
                renderKitClass.add(DEFAULT_RENDER_KIT_CLASS);
            }

            RenderKit renderKit = ClassUtils.buildApplicationObject(RenderKit.class, renderKitClass, null);
            // If the default html RenderKit instance is wrapped, the top level object will not implement
            // LazyRenderKit and all renderers will be added using the standard form.
            boolean lazyRenderKit = renderKit instanceof LazyRenderKit;

            for (Renderer element : dispenser.getRenderers(renderKitId))
            {
                jakarta.faces.render.Renderer renderer;
                
                if (element.getRendererClass() != null)
                {
                    if (lazyRenderKit)
                    {
                        // Add renderer using LazyRenderKit interface. This will have the effect of improve startup
                        // time avoiding load renderer classes that are not used.
                        ((LazyRenderKit) renderKit).addRenderer(element.getComponentFamily(), 
                            element.getRendererType(), element.getRendererClass());
                    }
                    else
                    {
                        // Use standard form
                        try
                        {
                            renderer = (jakarta.faces.render.Renderer) ClassUtils.newInstance(
                                element.getRendererClass());
                        }
                        catch (Throwable e)
                        {
                            // ignore the failure so that the render kit is configured
                            log.log(Level.SEVERE, "failed to configure class " + element.getRendererClass(), e);
                            continue;
                        }
                        if (renderer != null)
                        {
                            renderKit.addRenderer(element.getComponentFamily(), element.getRendererType(), renderer);
                        }
                        else
                        {
                            log.log(Level.INFO, "Renderer instance cannot be created for "+
                                    element.getRendererClass()+ ", ignoring..." + 
                                    element.getRendererClass());
                        }
                    }
                }
                else
                {
                        log.log(Level.INFO, "Renderer element with no rendererClass found, ignoring..." +
                                element.getRendererClass());
                }

            }
            
            Collection<ClientBehaviorRenderer> clientBehaviorRenderers
                    = dispenser.getClientBehaviorRenderers(renderKitId);

            // Add in client behavior renderers.

            for (ClientBehaviorRenderer clientBehaviorRenderer : clientBehaviorRenderers)
            {
                try
                {
                    jakarta.faces.render.ClientBehaviorRenderer behaviorRenderer
                            = (jakarta.faces.render.ClientBehaviorRenderer)
                            ClassUtils.newInstance(clientBehaviorRenderer.getRendererClass());

                    renderKit.addClientBehaviorRenderer(clientBehaviorRenderer.getRendererType(), behaviorRenderer);
                }

                catch (Throwable e)
                {
                    // Ignore.

                    if (log.isLoggable(Level.SEVERE))
                    {
                        log.log(Level.SEVERE, "failed to configure client behavior renderer class " +
                                clientBehaviorRenderer.getRendererClass(), e);
                    }
                }
            }

            renderKitFactory.addRenderKit(renderKitId, renderKit);
        }
    }

    private void configureLifecycle()
    {
        // create the lifecycle used by the app
        LifecycleFactory lifecycleFactory
                = (LifecycleFactory) FactoryFinder.getFactory(FactoryFinder.LIFECYCLE_FACTORY);

        for (Iterator<String> it = lifecycleFactory.getLifecycleIds(); it.hasNext();)
        {
            Lifecycle lifecycle = lifecycleFactory.getLifecycle(it.next());
            
            // add phase listeners
            for (String listenerClassName : getDispenser().getLifecyclePhaseListeners())
            {
                try
                {
                    PhaseListener listener = (PhaseListener)
                            ClassUtils.newInstance(listenerClassName, PhaseListener.class);
                    _callInjectAndPostConstruct(listener);
                    lifecycle.addPhaseListener(listener);
                }
                catch (ClassCastException e)
                {
                    log.severe("Class " + listenerClassName + " does not implement PhaseListener");
                }
            }

            // if ProjectStage is Development, install the DebugPhaseListener
            FacesContext facesContext = getFacesContext();
            if (facesContext.isProjectStage(ProjectStage.Development)
                    && _myfacesConfig.isDebugPhaseListenerEnabled())
            {
                lifecycle.addPhaseListener(new DebugPhaseListener());
            }
            if (facesContext.isProjectStage(ProjectStage.Development)
                    && _myfacesConfig.isElResolverTracing())
            {
                lifecycle.addPhaseListener(new TracingELResolverPhaseListener());
            }
        }
    }


    private void handleSerialFactory()
    {
        String serialProvider = _externalContext.getInitParameter(StateUtils.SERIAL_FACTORY);
        SerialFactory serialFactory = null;

        if (serialProvider == null)
        {
            serialFactory = new DefaultSerialFactory();
        }
        else
        {
            try
            {
                serialFactory = (SerialFactory) ClassUtils.newInstance(serialProvider);

            }
            catch (ClassCastException e)
            {
                log.log(Level.SEVERE, "Make sure '" + serialProvider + "' implements the correct interface", e);
            }
            catch (Exception e)
            {
                log.log(Level.SEVERE, "", e);
            }
            finally
            {
                if (serialFactory == null)
                {
                    serialFactory = new DefaultSerialFactory();
                    log.severe("Using default serialization provider");
                }
            }

        }

        log.fine("Serialization provider : " + serialFactory.getClass());
        _externalContext.getApplicationMap().put(StateUtils.SERIAL_FACTORY, serialFactory);
    }

    private void configureFlowHandler()
    {
        FacesContext facesContext = getFacesContext();
        Application application = getApplication();
        
        FacesConfigData dispenser = getDispenser();
        
        if (!dispenser.getFacesFlowDefinitions().isEmpty())
        {
            // Faces Flow require client window enabled to work.
            FacesConfigurator.enableDefaultWindowMode(facesContext);
        }
        
        for (FacesFlowDefinition flowDefinition : dispenser.getFacesFlowDefinitions())
        {
            FlowImpl flow = new FlowImpl();
            
            // TODO: configure flow object
            flow.setId(flowDefinition.getId());
            flow.setDefiningDocumentId(flowDefinition.getDefiningDocumentId());
            
            flow.setStartNodeId(flowDefinition.getStartNode());
            
            if (StringUtils.isNotEmpty(flowDefinition.getInitializer()))
            {
                flow.setInitializer(application.getExpressionFactory().createMethodExpression(
                    facesContext.getELContext(), flowDefinition.getInitializer(), null, NO_PARAMETER_TYPES));
            }
            if (StringUtils.isNotEmpty(flowDefinition.getFinalizer()))
            {
                flow.setFinalizer(application.getExpressionFactory().createMethodExpression(
                    facesContext.getELContext(), flowDefinition.getFinalizer(), null, NO_PARAMETER_TYPES));
            }
            
            for (FacesFlowCall call : flowDefinition.getFlowCallList())
            {
                FlowCallNodeImpl node = new FlowCallNodeImpl(call.getId());
                if (call.getFlowReference() != null)
                {
                    node.setCalledFlowId(call.getFlowReference().getFlowId());
                    node.setCalledFlowDocumentId(call.getFlowReference().getFlowDocumentId());
                }

                for (FacesFlowParameter parameter : call.getOutboundParameterList())
                {
                    node.putOutboundParameter( parameter.getName(),
                        new ParameterImpl(parameter.getName(),
                        application.getExpressionFactory().createValueExpression(
                            facesContext.getELContext(), parameter.getValue(), Object.class)) );
                }
                flow.putFlowCall(node.getId(), node);
            }

            for (FacesFlowMethodCall methodCall : flowDefinition.getMethodCallList())
            {
                MethodCallNodeImpl node = new MethodCallNodeImpl(methodCall.getId());
                if (StringUtils.isNotEmpty(methodCall.getMethod()))
                {
                    node.setMethodExpression(
                        application.getExpressionFactory().createMethodExpression(
                            facesContext.getELContext(), methodCall.getMethod(), null, NO_PARAMETER_TYPES));
                }
                if (StringUtils.isNotEmpty(methodCall.getDefaultOutcome()))
                {
                    node.setOutcome(
                        application.getExpressionFactory().createValueExpression(
                                facesContext.getELContext(), methodCall.getDefaultOutcome(), Object.class));
                }
                for (FacesFlowMethodParameter parameter : methodCall.getParameterList())
                {
                    node.addParameter(
                        new ParameterImpl(parameter.getClassName(),
                        application.getExpressionFactory().createValueExpression(
                            facesContext.getELContext(), parameter.getValue(), Object.class)) );
                }
                flow.addMethodCall(node);
            }
            
            for (FacesFlowParameter parameter : flowDefinition.getInboundParameterList())
            {
                flow.putInboundParameter(parameter.getName(),
                    new ParameterImpl(parameter.getName(),
                    application.getExpressionFactory().createValueExpression(
                        facesContext.getELContext(), parameter.getValue(), Object.class)) );
            }
            
            for (NavigationRule rule : flowDefinition.getNavigationRuleList())
            {
                flow.addNavigationCases(rule.getFromViewId(), NavigationUtils.convertNavigationCasesToAPI(rule));
            }
            
            for (FacesFlowSwitch flowSwitch : flowDefinition.getSwitchList())
            {
                SwitchNodeImpl node = new SwitchNodeImpl(flowSwitch.getId());
                
                if (flowSwitch.getDefaultOutcome() != null &&
                    StringUtils.isNotEmpty(flowSwitch.getDefaultOutcome().getFromOutcome()))
                {
                    if (ELText.isLiteral(flowSwitch.getDefaultOutcome().getFromOutcome()))
                    {
                        node.setDefaultOutcome(flowSwitch.getDefaultOutcome().getFromOutcome());
                    }
                    else
                    {
                        node.setDefaultOutcome(
                            application.getExpressionFactory().createValueExpression(
                                facesContext.getELContext(), flowSwitch.getDefaultOutcome().getFromOutcome(),
                                Object.class));
                    }
                }
                
                for (NavigationCase navCase : flowSwitch.getNavigationCaseList())
                {
                    SwitchCaseImpl nodeCase = new SwitchCaseImpl();
                    nodeCase.setFromOutcome(navCase.getFromOutcome());
                    if (StringUtils.isNotEmpty(navCase.getIf()))
                    {
                        nodeCase.setCondition(
                            application.getExpressionFactory().createValueExpression(
                                facesContext.getELContext(), navCase.getIf(),
                                Object.class));
                    }
                    node.addCase(nodeCase);
                }
                
                flow.putSwitch(node.getId(), node);
            }
            
            for (FacesFlowView view : flowDefinition.getViewList())
            {
                ViewNodeImpl node = new ViewNodeImpl(view.getId(), view.getVdlDocument());
                flow.addView(node);
            }

            for (FacesFlowReturn flowReturn : flowDefinition.getReturnList())
            {
                ReturnNodeImpl node = new ReturnNodeImpl(flowReturn.getId());
                if (flowReturn.getNavigationCase() != null &&
                    StringUtils.isNotEmpty(flowReturn.getNavigationCase().getFromOutcome()))
                {
                    if (ELText.isLiteral(flowReturn.getNavigationCase().getFromOutcome()))
                    {
                        node.setFromOutcome(flowReturn.getNavigationCase().getFromOutcome());
                    }
                    else
                    {
                        node.setFromOutcome(
                            application.getExpressionFactory().createValueExpression(
                                facesContext.getELContext(), flowReturn.getNavigationCase().getFromOutcome(),
                                Object.class));
                    }
                }
                flow.putReturn(node.getId(), node);
            }
            
            flow.freeze();
            
            // Add the flow, so the config can be processed by the flow system and the
            // navigation system.
            application.getFlowHandler().addFlow(facesContext, flow);
        }
        
        configureAnnotatedFlows(facesContext);
    }
    
    public static void configureAnnotatedFlows(FacesContext facesContext)
    {
        FacesFlowProviderFactory factory = 
            FacesFlowProviderFactory.getFacesFlowProviderFactory(facesContext.getExternalContext());
        FacesFlowProvider provider = factory.getFacesFlowProvider(facesContext.getExternalContext());
        
        Iterator<Flow> it = provider.getAnnotatedFlows(facesContext);
        if (it == null)
        {
            return;
        }

        if (it.hasNext())
        {
            enableDefaultWindowMode(facesContext);
        }
        while (it.hasNext())
        {
            Flow flow = it.next();
            if (flow instanceof FlowImpl)
            {
                ((FlowImpl) flow).freeze();
            }
            facesContext.getApplication().getFlowHandler().addFlow(facesContext, flow);
        }
    }
    
    public static void enableDefaultWindowMode(FacesContext facesContext)
    {
        if (!isEnableDefaultWindowMode(facesContext.getExternalContext()))
        {
            String windowMode = WebConfigParamUtils.getStringInitParameter(
                    facesContext.getExternalContext(), 
                    ClientWindow.CLIENT_WINDOW_MODE_PARAM_NAME, null);
            
            if (windowMode == null)
            {
                //No window mode set, force window mode to url
                String defaultWindowMode = WebConfigParamUtils.getStringInitParameter(
                    facesContext.getExternalContext(), 
                    ClientWindow.CLIENT_WINDOW_MODE_PARAM_NAME, 
                    ClientWindowFactoryImpl.WINDOW_MODE_URL);

                log.info("The current configuration requires ClientWindow enabled, setting it to '"+
                    defaultWindowMode+ '\'');

                facesContext.getExternalContext().getApplicationMap().put(
                    ENABLE_DEFAULT_WINDOW_MODE, Boolean.TRUE);
            }
        }
    }
    
    public static boolean isEnableDefaultWindowMode(ExternalContext externalContext)
    {
        return Boolean.TRUE.equals(externalContext.
            getApplicationMap().get(ENABLE_DEFAULT_WINDOW_MODE));
    }

    public static boolean isUrlWindowMode(ExternalContext externalContext)
    {
        return isEnableDefaultWindowMode(externalContext)
                || ClientWindowFactoryImpl.WINDOW_MODE_URL.equals(
                        externalContext.getInitParameter(ClientWindow.CLIENT_WINDOW_MODE_PARAM_NAME));
    }

    public void configureProtectedViews()
    {
        Application application = getApplication();

        FacesConfigData dispenser = getDispenser();
        //Protected Views
        ViewHandler viewHandler = application.getViewHandler();
        for (String urlPattern : dispenser.getProtectedViewUrlPatterns())
        {
            viewHandler.addProtectedView(urlPattern);
        }
    }
    
    protected InjectionProvider getInjectionProvider()
    {
        if (_injectionProvider == null)
        {
            _injectionProvider = InjectionProviderFactory.getInjectionProviderFactory(
                _externalContext).getInjectionProvider(_externalContext);
        }
        return _injectionProvider;
    }
    
    protected FacesContext getFacesContext()
    {
        if (_facesContext == null)
        {
            _facesContext = FacesContext.getCurrentInstance();
        }
        return _facesContext;
    }
    
    protected Application getApplication()
    {
        if (_application == null)
        {
            return getFacesContext().getApplication();
        }
        return _application;
    }
    
    protected void setApplication(Application application)
    {
        this._application = application;
    }
}

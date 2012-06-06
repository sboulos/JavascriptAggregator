/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.modulebuilder;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.IExtensionInitializer;
import com.ibm.jaggr.service.IRequestListener;
import com.ibm.jaggr.service.IExtensionInitializer.IExtensionRegistrar;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.transport.IHttpTransport;

/**
 * This interface is implemented by module builders.
 * <p>
 * Instances of this interface are created by the eclipse extension framework
 * for Aggregator extensions that implement the
 * {@code com.ibm.jaggr.service.modulebuilder} extension point.
 * Aggregator extensions may also register module builders by calling
 * {@link IExtensionRegistrar#registerExtension} when the extension's
 * {@link IExtensionInitializer#initialize} method is called (assuming the
 * extension implements the {@link IExtensionInitializer} interface).
 * <p>
 * The extension point defines the {@code plugin} and {@code extension}
 * attributes which are used by the aggregator to select the extension
 * implementation based on the module being built. See
 * {@link IAggregator#getModuleBuilder} for a description of how the aggregator
 * selects an {@code IMoudleBuilder} for a module.
 * <p>
 * Module builders provide the processed and minified (built) content for the
 * type of module being requested. The built output may vary depending on
 * request parameters. In this case, implementors need to provide a
 * {@link ICacheKeyGenerator} which will be used to obtain a unique identifier
 * for a build with the given request parameters. The keys from this object will
 * be used by the module cache manager to locate cached builds that can satisfy
 * the current request, and by the layer cache manager to identify cached
 * layers.
 * <p>
 * Module builds are provided as javascript code that defines an AMD module. For
 * non-javascript types of content, the content must be provided as a javascript
 * string wrapped in a define function similar to the following:
 * <p>
 * <code>define([], 'Content goes here');</code>
 * <p>
 * The above example is for an anonymous module. If the request attribute
 * specified by {@link IHttpTransport#EXPORTMODULENAMES_REQATTRNAME} is true,
 * then a named module must be provided with the module name specified as the
 * first parameter of the define function.
 * <p>
 * <code>define("foo/bar", [], 'Content goes here');</code>
 * <p>
 * It is assumed that there is a loader plugin on the client that requested the
 * module which knows how to extract the content from the module and work with
 * it, or transform it if necessary. For many non-javascript resources, the text
 * plugin provided by the {@link IHttpTransport} extension can be relied upon to
 * handle the content on the client.
 * <p>
 * Each of the methods in this interface can be called concurrently for the same
 * or different requests, so implementors must take appropriate steps to guard
 * against race conditions and other threading issues common in multi-threaded
 * programs. In particular, module builders must not set request attributes
 * directly since this operation is not thread-safe. Instead, module builders
 * may set properties in the ConcurrentMap which may be retrieved from the
 * request attribute named {@link IAggregator#CONCURRENTMAP_REQATTRNAME}.
 * 
 * @author chuckd@us.ibm.com
 */
public interface IModuleBuilder {

	/**
	 * Returns a {@link ModuleBuild} object containing the processed (built)
	 * output for the requested module.
	 * <p>
	 * If an error occurs, this method can throw an exception, or else let an
	 * exception that occurs at a lower level propagate up. How the exception
	 * gets handled depends largely on whether or not development mode is
	 * enabled.
	 * <p>
	 * If development mode is not enabled, then the exception is converted to a
	 * ServletException and allowed to propagate to the servlet container. This
	 * will result in an error response being returned to the client.
	 * <p>
	 * If development mode is enabled, then exceptions thrown by this method are
	 * handled by creating a module build containing code to invoke
	 * console.error() on the client, specifying the exception message as the
	 * error text, and the aggregated response is flagged as an error response
	 * to prevent caching of the response either on the server or on the client.
	 * The content value which is returned by the define function is an empty
	 * string.
	 * <p>
	 * If a builder would like to handle errors in development mode differently,
	 * either by providing partial content or to have more control over what is
	 * displayed in the client console, then this method can return the error
	 * content in the build output and use the {@link ModuleBuild} constructor
	 * that allows you to specify an error flag. Specifying true for the error
	 * flag will cause the aggregated response containing this module build to
	 * be flagged as an error and the build output will not be cached either on
	 * the server or on the client.
	 * <p>
	 * If the request attribute specified by
	 * {@link IHttpTransport#EXPORTMODULENAMES_REQATTRNAME} is false, then this
	 * method SHOULD return an anonymous module, however, if the request
	 * attribute is true, then this method MUST return a named module (the first
	 * parameter of the define function for the module must be the value
	 * specified by {@code mid}).
	 * <p>
	 * If, for some reason, a module builder is unable to provide named modules
	 * for a request, then the module builder should register a
	 * {@link IRequestListener} service with the OSGi service registry,
	 * specifying the name of the aggregator as the {@code name} property of the
	 * service, and then set the value of the
	 * {@link IHttpTransport#EXPORTMODULENAMES_REQATTRNAME} request attribute to
	 * false in the {@link IRequestListener#startRequest} method.
	 * <p>
	 * This method may choose to return a build containing multiple named
	 * modules if the request attribute specified by
	 * {@link IHttpTransport#EXPORTMODULENAMES_REQATTRNAME} is true. A builder
	 * that provides i18n resource modules, for example, may choose to include
	 * additional, locale specific, resource modules based on the request
	 * locale(s), however, this may only be done if all the modules in a
	 * response are named. If anonymous modules are being requested (i.e. the
	 * request attribute specified by
	 * {@link IHttpTransport#EXPORTMODULENAMES_REQATTRNAME} is false), then only
	 * the requested module may be included in the build output, regardless of
	 * whether or not the module builds provided by this builder are named.
	 * <p>
	 * This method must not return a provisional cache key generator. This
	 * requirement is enforced by the {@code ModuleBuild} constructor, which
	 * throws an {@code IllegalStateException} if a provisional cache key
	 * generator is used to construct the object.
	 * 
	 * @param mid
	 *            The module id
	 * @param resource
	 *            The resource object for the source module.
	 * @param request
	 *            The HTTP request object
	 * @param keyGens
	 *            Array of cache key generators for this module that was
	 *            obtained by a previous call to this method or
	 *            {@link #getCacheKeyGenerators(IAggregator)}. If
	 *            {@code keyGens} specifies a provisional cache key generator,
	 *            then the {@code ModuleBuild} returned by this method MUST
	 *            specify a non-provisional cache key generator. Otherwise, this
	 *            method SHOULD return a {@code ModuleBuild} object that
	 *            specifies {@code keyGen} for the cache key generator.
	 * 
	 * @return The processed (built) content as a {@link ModuleBuild} object
	 * @throws Exception
	 */
	public ModuleBuild build(String mid, IResource resource,
			HttpServletRequest request, ICacheKeyGenerator[] keyGens)
			throws Exception;

	/**
	 * This method may be called, before
	 * {@link #build(String, IResource, HttpServletRequest, ICacheKeyGenerator[])}
	 * is called by a separate worker thread, to obtain a cache key generator
	 * for this builder. If cache keys for this builder depend on module
	 * content, then this method should return a provisional cache key
	 * generator, which generates cache keys based on information in the request
	 * only. Provisional cache keys may be more exclusive than non-provisional
	 * cache keys in matching requests to module builds.
	 * <p>
	 * If this method returns a provisional cache key generator, then
	 * {@link #build(String, IResource, HttpServletRequest, ICacheKeyGenerator[])}
	 * MUST return a non-provisional cache key generator in the returned
	 * {@link ModuleBuild} object when subsequently called for the same request.
	 * 
	 * @param aggregator
	 *            The aggregator instance
	 * 
	 * @return The cache key generator for this builder, or null if the build
	 *         output does not have dependencies on any request parameters.
	 */
	public ICacheKeyGenerator[] getCacheKeyGenerators(IAggregator aggregator);

	public boolean handles(String mid, IResource resource);
}
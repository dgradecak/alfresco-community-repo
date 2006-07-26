/*
 * Copyright (C) 2005 Alfresco, Inc.
 *
 * Licensed under the Mozilla Public License version 1.1 
 * with a permitted attribution clause. You may obtain a
 * copy of the License at
 *
 *   http://www.alfresco.org/legal/license.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.alfresco.repo.jscript;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.permissions.AccessDeniedException;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.dictionary.InvalidAspectException;
import org.alfresco.service.cmr.lock.LockStatus;
import org.alfresco.service.cmr.model.FileExistsException;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.InvalidNodeRefException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.TemplateImageResolver;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Wrapper;
import org.springframework.util.StringUtils;

/**
 * Node class implementation, specific for use by ScriptService as part of the object model.
 * <p>
 * The class exposes Node properties, children and assocs as dynamically populated maps and lists.
 * The various collection classes are mirrored as JavaScript properties. So can be accessed using
 * standard JavaScript property syntax, such as <code>node.children[0].properties.name</code>.
 * <p>
 * Various helper methods are provided to access common and useful node variables such
 * as the content url and type information. 
 * 
 * @author Kevin Roast
 */
public final class Node implements Serializable, Scopeable
{
    private static Log logger = LogFactory.getLog(Node.class);
    
    private final static String NAMESPACE_BEGIN = "" + QName.NAMESPACE_BEGIN;
    private final static String CONTENT_DEFAULT_URL = "/download/direct/{0}/{1}/{2}/{3}";
    private final static String CONTENT_PROP_URL    = "/download/direct/{0}/{1}/{2}/{3}?property={4}";
    private final static String FOLDER_BROWSE_URL   = "/navigate/browse/{0}/{1}/{2}";
    
    /** Root scope for this object */
    private Scriptable scope;
    
    /** Cached values */
    private NodeRef nodeRef;
    private String name;
    private QName type;
    private String id;
    /** The aspects applied to this node */
    private Set<QName> aspects = null;
    /** The associations from this node */
    private ScriptableQNameMap<String, Node[]> assocs = null;
    /** The children of this node */
    private Node[] children = null;
    /** The properties of this node */
    private ScriptableQNameMap<String, Serializable> properties = null;
    private ServiceRegistry services = null;
    private NodeService nodeService = null;
    private Boolean isDocument = null;
    private Boolean isContainer = null;
    private String displayPath = null;
    private String mimetype = null;
    private Long size = null;
    private TemplateImageResolver imageResolver = null;
    private Node parent = null;
    private ChildAssociationRef primaryParentAssoc = null;
    // NOTE: see the reset() method when adding new cached members!
    
    
    // ------------------------------------------------------------------------------
    // Construction
    
    /**
     * Constructor
     * 
     * @param nodeRef       The NodeRef this Node wrapper represents
     * @param services      The ServiceRegistry the Node can use to access services
     * @param resolver      Image resolver to use to retrieve icons
     */
    public Node(NodeRef nodeRef, ServiceRegistry services, TemplateImageResolver resolver)
    {
        if (nodeRef == null)
        {
            throw new IllegalArgumentException("NodeRef must be supplied.");
        }
      
        if (services == null)
        {
            throw new IllegalArgumentException("The ServiceRegistry must be supplied.");
        }
        
        this.nodeRef = nodeRef;
        this.id = nodeRef.getId();
        this.services = services;
        this.nodeService = services.getNodeService();
        this.imageResolver = resolver;
    }
    
    /**
     * @see org.alfresco.repo.jscript.Scopeable#setScope(org.mozilla.javascript.Scriptable)
     */
    public void setScope(Scriptable scope)
    {
        this.scope = scope;
    }
    
    
    // ------------------------------------------------------------------------------
    // Node Wrapper API 
    
    /**
     * @return The GUID for the node
     */
    public String getId()
    {
        return this.id;
    }
    
    public String jsGet_id()
    {
        return getId();
    }
    
    /**
     * @return Returns the NodeRef this Node object represents
     */
    public NodeRef getNodeRef()
    {
        return this.nodeRef;
    }
    
    public String jsGet_nodeRef()
    {
        return getNodeRef().toString();
    }
    
    /**
     * @return Returns the type.
     */
    public QName getType()
    {
        if (this.type == null)
        {
            this.type = this.nodeService.getType(this.nodeRef);
        }
        
        return type;
    }
    
    public String jsGet_type()
    {
        return getType().toString();
    }
    
    /**
     * @return Helper to return the 'name' property for the node
     */
    public String getName()
    {
        if (this.name == null)
        {
            // try and get the name from the properties first
            this.name = (String)getProperties().get("cm:name");
            
            // if we didn't find it as a property get the name from the association name
            if (this.name == null)
            {
                ChildAssociationRef parentRef = this.nodeService.getPrimaryParent(this.nodeRef);
                if (parentRef != null && parentRef.getQName() != null)
                {
                    this.name = parentRef.getQName().getLocalName();
                }
                else
                {
                    this.name = "";
                }
            }
        }
        
        return this.name;
    }
    
    public String jsGet_name()
    {
        return getName();
    }
    
    /**
     * Helper to set the 'name' property for the node.
     * 
     * @param name      Name to set
     */
    public void setName(String name)
    {
        if (name != null)
        {
            this.getProperties().put(ContentModel.PROP_NAME.toString(), name.toString());
        }
    }
    
    public void jsSet_name(String name)
    {
        setName(name);
    }
    
    /**
     * @return The children of this Node as Node wrappers
     */
    public Node[] getChildren()
    {
        if (this.children == null)
        {
            List<ChildAssociationRef> childRefs = this.nodeService.getChildAssocs(this.nodeRef);
            this.children = new Node[childRefs.size()];
            for (int i=0; i<childRefs.size(); i++)
            {
                // create our Node representation from the NodeRef
                Node child = new Node(childRefs.get(i).getChildRef(), this.services, this.imageResolver);
                child.setScope(this.scope);
                this.children[i] = child;
            }
        }
        
        return this.children;
    }
    
    public Node[] jsGet_children()
    {
        return getChildren();
    }
    
    /**
     * @return Returns the Node at the specified 'cm:name' based Path walking the children of this Node.
     *         So a valid call might be <code>mynode.childByNamePath("/QA/Testing/Docs");</code>
     */
    public Node childByNamePath(String path)
    {
        // convert the name based path to a valid XPath query 
        StringBuilder xpath = new StringBuilder(path.length() << 1);
        for (StringTokenizer t = new StringTokenizer(path, "/"); t.hasMoreTokens(); /**/)
        {
            if (xpath.length() != 0)
            {
                xpath.append('/');
            }
            xpath.append("*[@cm:name='")
                 .append(t.nextToken())   // TODO: use QueryParameterDefinition see FileFolderService.search()
                 .append("']");
        }
        
        Node[] nodes = getChildrenByXPath(xpath.toString(), true);
        
        return (nodes.length != 0) ? nodes[0] : null;
    }
    
    // TODO: find out why this doesn't work - the function defs do not seem to get found
    //public Node jsFunction_childByNamePath(String path)
    //{
    //    return getChildByNamePath(path);
    //}
    
    /**
     * @return Returns the Nodes at the specified XPath walking the children of this Node.
     *         So a valid call might be <code>mynode.childrenByXPath("*[@cm:name='Testing']/*");</code>
     */
    public Node[] childrenByXPath(String xpath)
    {
        return getChildrenByXPath(xpath, false);
    }
    
    // TODO: find out why this doesn't work - the function defs do not seem to get found
    //public Node[] jsFunction_childrenByXPath(String xpath)
    //{
    //    return childrenByXPath(xpath);
    //}
    
    /**
     * Return the associations for this Node. As a Map of assoc name to an Array of Nodes.
     * 
     * The Map returned implements the Scriptable interface to allow access to the assoc arrays via
     * JavaScript associative array access. This means associations of this node can be access thus:
     * <code>node.assocs["translations"][0]</code>
     * 
     * @return associations as a Map of assoc name to an Array of Nodes.
     */
    public Map<String, Node[]> getAssocs()
    {
        if (this.assocs == null)
        {
            // this Map implements the Scriptable interface for native JS syntax property access
            this.assocs = new ScriptableQNameMap<String, Node[]>(this.services.getNamespaceService());
            
            List<AssociationRef> refs = this.nodeService.getTargetAssocs(this.nodeRef, RegexQNamePattern.MATCH_ALL);
            for (AssociationRef ref : refs)
            {
                String qname = ref.getTypeQName().toString();
                Node[] nodes = (Node[])this.assocs.get(qname);
                if (nodes == null)
                {
                    // first access for the list for this qname
                    nodes = new Node[1];
                }
                else
                {
                    Node[] newNodes = new Node[nodes.length + 1];
                    System.arraycopy(nodes, 0, newNodes, 0, nodes.length);
                    nodes = newNodes;
                }
                nodes[nodes.length - 1] = new Node(ref.getTargetRef(), this.services, this.imageResolver);
                nodes[nodes.length - 1].setScope(this.scope);
                
                this.assocs.put(ref.getTypeQName().toString(), nodes);
            }
        }
        
        return this.assocs;
    }
    
    public Map<String, Node[]> jsGet_assocs()
    {
        return getAssocs();
    }
    
    /**
     * Return all the properties known about this node.
     * 
     * The Map returned implements the Scriptable interface to allow access to the properties via
     * JavaScript associative array access. This means properties of a node can be access thus:
     * <code>node.properties["name"]</code>
     * 
     * @return Map of properties for this Node.
     */
    public Map<String, Object> getProperties()
    {
        if (this.properties == null)
        {
            // this Map implements the Scriptable interface for native JS syntax property access
            this.properties = new ScriptableQNameMap<String, Serializable>(this.services.getNamespaceService());
            
            Map<QName, Serializable> props = this.nodeService.getProperties(this.nodeRef);
            for (QName qname : props.keySet())
            {
                Serializable propValue = props.get(qname);
                
                // perform conversions from Java objects to JavaScript scriptable instances
                if (propValue instanceof NodeRef)
                {
                    // NodeRef object properties are converted to new Node objects
                    // so they can be used as objects within a template
                    propValue = new Node(((NodeRef)propValue), this.services, this.imageResolver);
                    ((Node)propValue).setScope(this.scope);
                }
                else if (propValue instanceof ContentData)
                {
                    // ContentData object properties are converted to ScriptContentData objects
                    // so the content and other properties of those objects can be accessed
                    propValue = new ScriptContentData((ContentData)propValue, qname);
                }
                else if (propValue instanceof Date)
                {
                    // convert Date to JavaScript native Date object
                    // call the "Date" constructor on the root scope object - passing in the millisecond
                    // value from the Java date - this will construct a JavaScript Date with the same value
                    Date date = (Date)propValue;
                    Object val = ScriptRuntime.newObject(
                            Context.getCurrentContext(), this.scope, "Date", new Object[] {date.getTime()});
                    propValue = (Serializable)val;
                }
                // simple numbers and strings are handled automatically by Rhino
                
                this.properties.put(qname.toString(), propValue);
            }
        }
        
        return this.properties;
    }
    
    public Map<String, Object> jsGet_properties()
    {
        return getProperties();
    }
    
    /**
     * @return true if this Node is a container (i.e. a folder)
     */
    public boolean isContainer()
    {
        if (isContainer == null)
        {
            DictionaryService dd = this.services.getDictionaryService();
            isContainer = Boolean.valueOf( (dd.isSubClass(getType(), ContentModel.TYPE_FOLDER) == true && 
                    dd.isSubClass(getType(), ContentModel.TYPE_SYSTEM_FOLDER) == false) );
        }
        
        return isContainer.booleanValue();
    }
    
    public boolean jsGet_isContainer()
    {
        return isContainer();
    }
    
    /**
     * @return true if this Node is a Document (i.e. with content)
     */
    public boolean isDocument()
    {
        if (isDocument == null)
        {
            DictionaryService dd = this.services.getDictionaryService();
            isDocument = Boolean.valueOf(dd.isSubClass(getType(), ContentModel.TYPE_CONTENT));
        }
        
        return isDocument.booleanValue();
    }
    
    public boolean jsGet_isDocument()
    {
        return isDocument();
    }
    
    /**
     * @return The list of aspects applied to this node
     */
    public Set<QName> getAspects()
    {
        if (this.aspects == null)
        {
            this.aspects = this.nodeService.getAspects(this.nodeRef);
        }
        
        return this.aspects;
    }
    
    public String[] jsGet_aspects()
    {
        Set<QName> aspects = getAspects();
        String[] result = new String[aspects.size()];
        int count = 0;
        for (QName qname : aspects)
        {
           result[count++] = qname.toString();
        }
        return result;
    }
    
    /**
     * @param aspect    The aspect name to test for (full qualified or short-name form)
     * 
     * @return true if the node has the aspect false otherwise
     */
    public boolean hasAspect(String aspect)
    {
        return getAspects().contains(createQName(aspect));
    }
    
    /**
     * Return true if the user has the specified permission on the node.
     * <p>
     * The default permissions are found in <code>org.alfresco.service.cmr.security.PermissionService</code>.
     * Most commonly used are "Write", "Delete" and "AddChildren".
     * 
     * @param permission as found in <code>org.alfresco.service.cmr.security.PermissionService</code>
     * 
     * @return true if the user has the specified permission on the node.
     */
    public boolean hasPermission(String permission)
    {
        boolean allowed = false;
        
        if (permission != null && permission.length() != 0)
        {
            AccessStatus status = this.services.getPermissionService().hasPermission(this.nodeRef, permission);
            allowed = (AccessStatus.ALLOWED == status);
        }
        
        return allowed;
    }
    
    /**
     * @return true if the node inherits permissions from the parent node, false otherwise
     */
    public boolean inheritsPermissions()
    {
       return this.services.getPermissionService().getInheritParentPermissions(this.nodeRef);
    }
    
    /**
     * Set whether this node should inherit permissions from the parent node.
     * 
     * @param inherit   True to inherit parent permissions, false otherwise.
     */
    public void setInheritsPermissions(boolean inherit)
    {
       this.services.getPermissionService().setInheritParentPermissions(this.nodeRef, inherit);
    }
    
    /**
     * Apply a permission for ALL users to the node.
     * 
     * @param permission   Permission to apply @see org.alfresco.service.cmr.security.PermissionService
     */
    public void setPermission(String permission)
    {
       this.services.getPermissionService().setPermission(this.nodeRef, PermissionService.ALL_AUTHORITIES, permission, true);
    }
    
    /**
     * Apply a permission for the specified authority (e.g. username or group) to the node.
     *  
     * @param permission   Permission to apply @see org.alfresco.service.cmr.security.PermissionService
     * @param authority    Authority (generally a username or group name) to apply the permission for
     */
    public void setPermission(String permission, String authority)
    {
       this.services.getPermissionService().setPermission(this.nodeRef, authority, permission, true);
    }
    
    /**
     * Remove a permission for ALL user from the node.
     * 
     * @param permission   Permission to remove @see org.alfresco.service.cmr.security.PermissionService
     */
    public void removePermission(String permission)
    {
       this.services.getPermissionService().deletePermission(this.nodeRef, PermissionService.ALL_AUTHORITIES, permission);
    }
    
    /**
     * Remove a permission for the specified authority (e.g. username or group) from the node.
     * 
     * @param permission   Permission to remove @see org.alfresco.service.cmr.security.PermissionService
     * @param authority    Authority (generally a username or group name) to apply the permission for
     */
    public void removePermission(String permission, String authority)
    {
       this.services.getPermissionService().deletePermission(this.nodeRef, authority, permission);
    }
    
    /**
     * @return Display path to this node
     */
    public String getDisplayPath()
    {
        if (displayPath == null)
        {
            try
            {
                displayPath = this.nodeService.getPath(this.nodeRef).toDisplayPath(this.nodeService);
            }
            catch (AccessDeniedException err)
            {
                displayPath = "";
            }
        }
        
        return displayPath;
    }
    
    public String jsGet_displayPath()
    {
        return getDisplayPath();
    }
    
    /**
     * @return the small icon image for this node
     */
    public String getIcon16()
    {
        if (this.imageResolver != null)
        {
            if (isDocument())
            {
                return this.imageResolver.resolveImagePathForName(getName(), true);
            }
            else
            {
                return "/images/icons/space_small.gif";
            }
        }
        else
        {
            return "/images/filetypes/_default.gif";
        }
    }
    
    public String jsGet_icon16()
    {
        return getIcon16();
    }
    
    /**
     * @return the large icon image for this node
     */
    public String getIcon32()
    {
        if (this.imageResolver != null)
        {
            if (isDocument())
            {
                return this.imageResolver.resolveImagePathForName(getName(), false);
            }
            else
            {
                String icon = (String)getProperties().get("app:icon");
                if (icon != null)
                {
                    return "/images/icons/" + icon + ".gif";
                }
                else
                {
                    return "/images/icons/space-icon-default.gif";
                }
            }
        }
        else
        {
            return "/images/filetypes32/_default.gif";
        }
    }
    
    public String jsGet_icon32()
    {
        return getIcon32();
    }
    
    /**
     * @return true if the node is currently locked
     */
    public boolean isLocked()
    {
        boolean locked = false;
        
        if (getAspects().contains(ContentModel.ASPECT_LOCKABLE))
        {
            LockStatus lockStatus = this.services.getLockService().getLockStatus(this.nodeRef);
            if (lockStatus == LockStatus.LOCKED || lockStatus == LockStatus.LOCK_OWNER)
            {
                locked = true;
            }
        }
        
        return locked;
    }
    
    public boolean jsGet_isLocked()
    {
        return isLocked();
    }
    
    /**
     * @return the parent node
     */
    public Node getParent()
    {
        if (parent == null)
        {
            NodeRef parentRef = this.nodeService.getPrimaryParent(nodeRef).getParentRef();
            // handle root node (no parent!)
            if (parentRef != null)
            {
                parent = new Node(parentRef, this.services, this.imageResolver);
                parent.setScope(this.scope);
            }
        }
        
        return parent;
    }
    
    public Node jsGet_parent()
    {
        return getParent();
    }
    
    /**
     * 
     * @return the primary parent association so we can get at the association QName and the association type QName.
     */
    public ChildAssociationRef getPrimaryParentAssoc()
    {
        if (primaryParentAssoc == null)
        {
            primaryParentAssoc = this.nodeService.getPrimaryParent(nodeRef);
        }
        return primaryParentAssoc;
    }
    
    public ChildAssociationRef jsGet_primaryParentAssoc()
    {
        return getPrimaryParentAssoc();
    }
    
    /**
     * @return the content String for this node from the default content property
     *         (@see ContentModel.PROP_CONTENT)
     */
    public String getContent()
    {
        String content = "";
        
        ScriptContentData contentData = (ScriptContentData)getProperties().get(ContentModel.PROP_CONTENT);
        if (contentData != null)
        {
            content = contentData.getContent();
        }
        
        return content;
    }
    
    public String jsGet_content()
    {
        return getContent();
    }
    
    /**
     * Set the content for this node
     * 
     * @param content       Content string to set
     */
    public void setContent(String content)
    {
        ScriptContentData contentData = (ScriptContentData)getProperties().get(ContentModel.PROP_CONTENT);
        if (contentData != null)
        {
            contentData.setContent(content);
        }
    }
    
    public void jsSet_content(String content)
    {
        setContent(content);
    }
    
    /**
     * @return For a content document, this method returns the URL to the content stream for
     *         the default content property (@see ContentModel.PROP_CONTENT)
     *         <p>
     *         For a container node, this method return the URL to browse to the folder in the web-client
     */
    public String getUrl()
    {
        if (isDocument() == true)
        {
           try
           {
               return MessageFormat.format(CONTENT_DEFAULT_URL, new Object[] {
                       nodeRef.getStoreRef().getProtocol(),
                       nodeRef.getStoreRef().getIdentifier(),
                       nodeRef.getId(),
                       StringUtils.replace(URLEncoder.encode(getName(), "UTF-8"), "+", "%20") } );
           }
           catch (UnsupportedEncodingException err)
           {
               throw new AlfrescoRuntimeException("Failed to encode content URL for node: " + nodeRef, err);
           }
        }
        else
        {
           return MessageFormat.format(FOLDER_BROWSE_URL, new Object[] {
                       nodeRef.getStoreRef().getProtocol(),
                       nodeRef.getStoreRef().getIdentifier(),
                       nodeRef.getId() } );
        }
    }
    
    public String jsGet_url()
    {
        return getUrl();
    }
    
    /**
     * @return The mimetype encoding for content attached to the node from the default content property
     *         (@see ContentModel.PROP_CONTENT)
     */
    public String getMimetype()
    {
        if (mimetype == null)
        {
            ScriptContentData content = (ScriptContentData)this.getProperties().get(ContentModel.PROP_CONTENT);
            if (content != null)
            {
                mimetype = content.getMimetype();
            }
        }
        
        return mimetype;
    }
    
    public String jsGet_mimetype()
    {
        return getMimetype();
    }
    
    /**
     * @return The size in bytes of the content attached to the node from the default content property
     *         (@see ContentModel.PROP_CONTENT)
     */
    public long getSize()
    {
        if (size == null)
        {
            ScriptContentData content = (ScriptContentData)this.getProperties().get(ContentModel.PROP_CONTENT);
            if (content != null)
            {
                size = content.getSize();
            }
        }
        
        return size != null ? size.longValue() : 0L;
    }
    
    public long jsGet_size()
    {
        return getSize();
    }
    
    /**
     * @return the image resolver instance used by this node
     */
    public TemplateImageResolver getImageResolver()
    {
        return this.imageResolver;
    }
    
    
    // ------------------------------------------------------------------------------
    // Create and Modify API  
    
    /**
     * Persist the properties of this Node.
     */
    public void save()
    {
        // persist properties back to the node in the DB 
        Map<QName, Serializable> props = new HashMap<QName, Serializable>(getProperties().size());
        for (String key : this.properties.keySet())
        {
            Serializable value = (Serializable)this.properties.get(key);
            
            // perform the conversion from script wrapper object to repo serializable values
            value = convertValue(value);
            
            props.put(createQName(key), value);
        }
        this.nodeService.setProperties(this.nodeRef, props);
    }

    /**
     * Convert an object from any script wrapper value to a valid repository serializable value.
     * This includes converting JavaScript Array objects to Lists of valid objects.
     * 
     * @param value     Value to convert from script wrapper object to repo serializable value
     * 
     * @return valid repo value
     */
    private static Serializable convertValue(Serializable value)
    {
        if (value instanceof Node)
        {
            // convert back to NodeRef
            value = ((Node)value).getNodeRef();
        }
        else if (value instanceof ScriptContentData)
        {
            // convert back to ContentData
            value = ((ScriptContentData)value).contentData;
        }
        else if (value instanceof Wrapper)
        {
            // unwrap a Java object from a JavaScript wrapper
            // recursively call this method to convert the unwrapped value
            value = convertValue((Serializable)((Wrapper)value).unwrap());
        }
        else if (value instanceof ScriptableObject)
        {
            // a scriptable object will probably indicate a multi-value property
            // set using a JavaScript Array object
            ScriptableObject values = (ScriptableObject)value;
            
            if (value instanceof NativeArray)
            {
               // convert JavaScript array of values to a List of Serializable objects
               Object[] propIds = values.getIds();
               List<Serializable> propValues = new ArrayList<Serializable>(propIds.length);
               for (int i=0; i<propIds.length; i++)
               {
                   // work on each key in turn
                   Object propId = propIds[i];
                   
                   // we are only interested in keys that indicate a list of values
                   if (propId instanceof Integer)
                   {
                       // get the value out for the specified key
                       Serializable val = (Serializable)values.get((Integer)propId, values);
                       // recursively call this method to convert the value
                       propValues.add(convertValue(val));
                   }
               }
               value = (Serializable)propValues;
            }
            else
            {
               // TODO: add code here to use the dictionary and convert to correct value type
               Object javaObj = Context.jsToJava(value, Date.class);
               if (javaObj instanceof Date)
               {
                  value = (Date)javaObj;
               }
            }
        }
        return value;
    }
    
    /**
     * Create a new File (cm:content) node as a child of this node.
     * <p>
     * Once created the file should have content set using the <code>content</code> property.
     * 
     * @param name      Name of the file to create
     * 
     * @return Newly created Node or null if failed to create.
     */
    public Node createFile(String name)
    {
        Node node = null;
        
        try
        {
            if (name != null && name.length() != 0)
            {
                FileInfo fileInfo = this.services.getFileFolderService().create(
                        this.nodeRef, name, ContentModel.TYPE_CONTENT);
                node = new Node(fileInfo.getNodeRef(), this.services, this.imageResolver);
                node.setScope(this.scope);
            }
        }
        catch (FileExistsException fileErr)
        {
            // default of null will be returned
            // TODO: how to report this kind of exception to the script writer?
        }
        catch (AccessDeniedException accessErr)
        {
            // default of null will be returned
        }
        
        return node;
    }
    
    /**
     * Create a new folder (cm:folder) node as a child of this node.
     * 
     * @param name      Name of the folder to create
     * 
     * @return Newly created Node or null if failed to create.
     */
    public Node createFolder(String name)
    {
        Node node = null;
        
        try
        {
            if (name != null && name.length() != 0)
            {
                FileInfo fileInfo = this.services.getFileFolderService().create(
                        this.nodeRef, name, ContentModel.TYPE_FOLDER);
                node = new Node(fileInfo.getNodeRef(), this.services, this.imageResolver);
                node.setScope(this.scope);
            }
        }
        catch (FileExistsException fileErr)
        {
            // default of null will be returned
            // TODO: how to report this kind of exception to the script writer?
        }
        catch (AccessDeniedException accessErr)
        {
            // default of null will be returned
        }
        
        return node;
    }
    
    /**
     * Create a new Node of the specified type as a child of this node.
     * 
     * @param name      Name of the node to create
     * @param type      QName type (can either be fully qualified or short form such as 'cm:content')
     * 
     * @return Newly created Node or null if failed to create.
     */
    public Node createNode(String name, String type)
    {
        Node node = null;
        
        try
        {
            if (name != null && name.length() != 0 &&
                type != null && type.length() != 0)
            {
                Map<QName, Serializable> props = new HashMap<QName, Serializable>(1);
                props.put(ContentModel.PROP_NAME, name);
                ChildAssociationRef childAssocRef = this.nodeService.createNode(
                        this.nodeRef,
                        ContentModel.ASSOC_CONTAINS,
                        QName.createQName(NamespaceService.ALFRESCO_URI, QName.createValidLocalName(name)),
                        createQName(type),
                        props);
                node = new Node(childAssocRef.getChildRef(), this.services, this.imageResolver);
                node.setScope(this.scope);
            }
        }
        catch (AccessDeniedException accessErr)
        {
            // default of null will be returned
        }
        
        return node;
    }
    
    /**
     * Remove this node. Any references to this Node or its NodeRef should be discarded!
     */
    public boolean remove()
    {
        boolean success = false;
        
        try
        {
            this.nodeService.deleteNode(this.nodeRef);
            
            reset();
            
            success = true;
        }
        catch (AccessDeniedException accessErr)
        {
            // default of false will be returned
        }
        catch (InvalidNodeRefException refErr)
        {
            // default of false will be returned
        }
        
        return success;
    }
    
    /**
     * Copy this Node to a new parent destination. Note that children of the source
     * Node are not copied.
     * 
     * @param destination Node
     * 
     * @return The newly copied Node instance or null if failed to copy.
     */
    public Node copy(Node destination)
    {
        return copy(destination, false);
    }
    
    /**
     * Copy this Node and potentially all child nodes to a new parent destination.
     * 
     * @param destination  Node
     * @param deepCopy     True for a deep copy, false otherwise.
     * 
     * @return The newly copied Node instance or null if failed to copy.
     */
    public Node copy(Node destination, boolean deepCopy)
    {
        Node copy = null;
        
        try
        {
            if (destination != null)
            {
                NodeRef copyRef = this.services.getCopyService().copy(
                        this.nodeRef,
                        destination.getNodeRef(),
                        ContentModel.ASSOC_CONTAINS,
                        getPrimaryParentAssoc().getQName(),
                        deepCopy);
                copy = new Node(copyRef, this.services, this.imageResolver);
                copy.setScope(this.scope);
            }
        }
        catch (AccessDeniedException accessErr)
        {
            // default of null will be returned
        }
        catch (InvalidNodeRefException nodeErr)
        {
            // default of null will be returned
        }
        
        return copy;
    }
    
    /**
     * Move this Node to a new parent destination.
     * 
     * @param destination Node
     * 
     * @return true on successful move, false on failure to move.
     */
    public boolean move(Node destination)
    {
        boolean success = false;
        
        try
        {
            if (destination != null)
            {
                this.primaryParentAssoc = this.nodeService.moveNode(
                        this.nodeRef,
                        destination.getNodeRef(),
                        ContentModel.ASSOC_CONTAINS,
                        getPrimaryParentAssoc().getQName());
                
                // reset cached values
                reset();
                
                success = true;
            }
        }
        catch (AccessDeniedException accessErr)
        {
            // default of false will be returned
        }
        catch (InvalidNodeRefException refErr)
        {
            // default of false will be returned
        }
        
        return success;
    }
    
    /**
     * Add an aspect to the Node. As no properties are provided in this call, it can only be
     * used to add aspects that do not require any mandatory properties.
     * 
     * @param type      Type name of the aspect to add
     *                  
     * @return true if the aspect was added successfully, false if an error occured.
     */
    public boolean addAspect(String type)
    {
        return addAspect(type, null);
    }
    
    /**
     * Add an aspect to the Node.
     * 
     * @param type      Type name of the aspect to add
     * @param props     Object (generally an assocative array) providing the named properties
     *                  for the aspect - any mandatory properties for the aspect must be provided!
     *                  
     * @return true if the aspect was added successfully, false if an error occured.
     */
    public boolean addAspect(String type, Object properties)
    {
        boolean success = false;
        
        if (type != null && type.length() != 0)
        {
            try
            {
                Map<QName, Serializable> aspectProps = null;
                if (properties instanceof ScriptableObject)
                {
                    ScriptableObject props = (ScriptableObject)properties;
                    // we need to get all the keys to the properties provided
                    // and convert them to a Map of QName to Serializable objects
                    Object[] propIds = props.getIds();
                    aspectProps = new HashMap<QName, Serializable>(propIds.length);
                    for (int i=0; i<propIds.length; i++)
                    {
                        // work on each key in turn
                        Object propId = propIds[i];
                        
                        // we are only interested in keys that are formed of Strings i.e. QName.toString()
                        if (propId instanceof String)
                        {
                            // get the value out for the specified key - make sure it is Serializable
                            Object value = props.get((String)propId, props);
                            value = convertValue((Serializable)value);
                            aspectProps.put(createQName((String)propId), (Serializable)value);
                        }
                    }
                }
                QName aspectQName = createQName(type);
                this.nodeService.addAspect(this.nodeRef, aspectQName, aspectProps);
                
                // reset the relevant cached node members
                reset();
                
                success = true;
            }
            catch (InvalidAspectException aspectErr)
            {
                // default of failed will be returned
            }
        }
        
        return success;
    }
    
    /**
     * Helper to create a QName from either a fully qualified or short-name QName string
     * 
     * @param s     Fully qualified or short-name QName string
     * 
     * @return QName
     */
    private QName createQName(String s)
    {
        QName qname;
        if (s.indexOf(NAMESPACE_BEGIN) != -1)
        {
            qname = QName.createQName(s);
        }
        else
        {
            qname = QName.createQName(s, this.services.getNamespaceService());
        }
        return qname;
    }
    
    /**
     * Reset the Node cached state
     */
    private void reset()
    {
       this.name = null;
       this.type = null;
       this.properties = null;
       this.aspects = null;
       this.assocs = null;
       this.children = null;
       this.displayPath = null;
       this.isDocument = null;
       this.isContainer = null;
       this.mimetype = null;
       this.size = null;
       this.parent = null;
       this.primaryParentAssoc = null;
    }
    
    /**
     * Override Object.toString() to provide useful debug output
     */
    public String toString()
    {
        if (this.nodeService.exists(nodeRef))
        {
            return "Node Type: " + getType() + 
                   "\nNode Properties: " + this.getProperties().toString() + 
                   "\nNode Aspects: " + this.getAspects().toString();
        }
        else
        {
            return "Node no longer exists: " + nodeRef;
        }
    }
    
    
    // ------------------------------------------------------------------------------
    // Private Helpers 
    
    /**
     * Return a list or a single Node from executing an xpath against the parent Node.
     * 
     * @param xpath        XPath to execute
     * @param firstOnly    True to return the first result only
     * 
     * @return Node[] can be empty but never null
     */
    private Node[] getChildrenByXPath(String xpath, boolean firstOnly)
    {
        Node[] result = null;
        
        if (xpath.length() != 0)
        {
            if (logger.isDebugEnabled())
                logger.debug("Executing xpath: " + xpath);
            
            List<NodeRef> nodes = this.services.getSearchService().selectNodes(
                    this.nodeRef,
                    xpath,
                    null,
                    this.services.getNamespaceService(),
                    false);
            
            // see if we only want the first result
            if (firstOnly == true)
            {
                if (nodes.size() != 0)
                {
                    result = new Node[1];
                    result[0] = new Node(nodes.get(0), this.services, this.imageResolver);
                    result[0].setScope(this.scope);
                }
            }
            // or all the results
            else
            {
                result = new Node[nodes.size()];
                for (int i=0; i<nodes.size(); i++)
                {
                    NodeRef ref = nodes.get(i);
                    result[i] = new Node(ref, this.services, this.imageResolver);
                    result[i].setScope(this.scope);
                }
            }
        }
        
        return result != null ? result : new Node[0];
    }
    
    
    // ------------------------------------------------------------------------------
    // Inner Classes
    
    /**
     * Inner class wrapping and providing access to a ContentData property 
     */
    public class ScriptContentData implements Serializable
    {
       /**
        * Constructor
        * 
        * @param contentData  The ContentData object this object wraps 
        * @param property     The property the ContentData is attached too
        */
        public ScriptContentData(ContentData contentData, QName property)
        {
            this.contentData = contentData;
            this.property = property;
        }
        
        /**
         * @return the content stream
         */
        public String getContent()
        {
            ContentService contentService = services.getContentService();
            ContentReader reader = contentService.getReader(nodeRef, property);
            
            return (reader != null && reader.exists()) ? reader.getContentString() : "";
        }
        
        public String jsGet_content()
        {
            return getContent();
        }
        
        /**
         * Set the content stream
         * 
         * @param content   Content string to set
         */
        public void setContent(String content)
        {
            ContentService contentService = services.getContentService();
            ContentWriter writer = contentService.getWriter(nodeRef, this.property, true);
            writer.setMimetype(getMimetype());  // use existing mimetype value
            writer.putContent(content);
            
            // update cached variables after putContent()
            this.contentData = (ContentData)services.getNodeService().getProperty(nodeRef, this.property);
        }
        
        public void jsSet_content(String content)
        {
            setContent(content);
        }
        
        /**
         * @return download URL to the content
         */
        public String getUrl()
        {
            try
            {
                return MessageFormat.format(CONTENT_PROP_URL, new Object[] {
                       nodeRef.getStoreRef().getProtocol(),
                       nodeRef.getStoreRef().getIdentifier(),
                       nodeRef.getId(),
                       StringUtils.replace(URLEncoder.encode(getName(), "UTF-8"), "+", "%20"),
                       StringUtils.replace(URLEncoder.encode(property.toString(), "UTF-8"), "+", "%20") } );
            }
            catch (UnsupportedEncodingException err)
            {
                throw new AlfrescoRuntimeException("Failed to encode content URL for node: " + nodeRef, err);
            }
        }
        
        public String jsGet_url()
        {
            return getUrl();
        }
        
        public long getSize()
        {
            return contentData.getSize();
        }
        
        public long jsGet_size()
        {
            return getSize();
        }
        
        public String getMimetype()
        {
            return contentData.getMimetype();
        }
        
        public String jsGet_mimetype()
        {
            return getMimetype();
        }
        
        private ContentData contentData;
        private QName property;
    }
}
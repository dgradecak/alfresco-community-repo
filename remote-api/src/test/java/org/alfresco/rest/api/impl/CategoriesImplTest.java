/*
 * #%L
 * Alfresco Remote API
 * %%
 * Copyright (C) 2005 - 2022 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software.
 * If the software was purchased under a paid Alfresco license, the terms of
 * the paid license agreement will prevail.  Otherwise, the software is
 * provided under the following open source license terms:
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

package org.alfresco.rest.api.impl;

import static org.alfresco.rest.api.Nodes.PATH_ROOT;
import static org.alfresco.rest.api.impl.CategoriesImpl.NOT_A_VALID_CATEGORY;
import static org.alfresco.rest.api.impl.CategoriesImpl.NOT_NULL_OR_EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.alfresco.model.ContentModel;
import org.alfresco.rest.api.Nodes;
import org.alfresco.rest.api.model.Category;
import org.alfresco.rest.api.model.Node;
import org.alfresco.rest.framework.core.exceptions.EntityNotFoundException;
import org.alfresco.rest.framework.core.exceptions.InvalidArgumentException;
import org.alfresco.rest.framework.core.exceptions.PermissionDeniedException;
import org.alfresco.rest.framework.resource.parameters.CollectionWithPagingInfo;
import org.alfresco.rest.framework.resource.parameters.Parameters;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.CategoryService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CategoriesImplTest
{
    private static final String CATEGORY_ID = "category-node-id";
    private static final String CATEGORY_NAME = "categoryName";
    private static final String PARENT_ID = "parent-node-id";
    private static final String CAT_ROOT_NODE_ID = "cat-root-node-id";
    private static final NodeRef CATEGORY_NODE_REF = createNodeRefWithId(CATEGORY_ID);
    private static final Category CATEGORY = createDefaultCategoryWithName(CATEGORY_NAME);

    @Mock
    private Nodes nodesMock;
    @Mock
    private NodeService nodeServiceMock;
    @Mock
    private Parameters parametersMock;
    @Mock
    private AuthorityService authorityServiceMock;
    @Mock
    private CategoryService categoryServiceMock;
    @Mock
    private ChildAssociationRef dummyChildAssociationRefMock;
    @Mock
    private ChildAssociationRef categoryChildAssociationRefMock;

    @InjectMocks
    private CategoriesImpl objectUnderTest;

    @Before
    public void setUp() throws Exception
    {
        given(authorityServiceMock.hasAdminAuthority()).willReturn(true);
        given(nodesMock.validateNode(eq(CATEGORY_ID))).willReturn(CATEGORY_NODE_REF);
        given(nodesMock.isSubClass(any(), any(), anyBoolean())).willReturn(true);
    }

    @Test
    public void shouldNotGetRootCategoryById()
    {
        final NodeRef categoryRootNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, CAT_ROOT_NODE_ID);
        given(nodesMock.validateNode(CAT_ROOT_NODE_ID)).willReturn(categoryRootNodeRef);
        given(categoryChildAssociationRefMock.getQName()).willReturn(ContentModel.ASPECT_GEN_CLASSIFIABLE);
        given(nodeServiceMock.getParentAssocs(categoryRootNodeRef)).willReturn(List.of(categoryChildAssociationRefMock));

        //when
        assertThrows(InvalidArgumentException.class, () -> objectUnderTest.getCategoryById(CAT_ROOT_NODE_ID, parametersMock));

        then(nodesMock).should().validateNode(CAT_ROOT_NODE_ID);
        then(nodesMock).should().isSubClass(categoryRootNodeRef, ContentModel.TYPE_CATEGORY, false);
        then(nodesMock).shouldHaveNoMoreInteractions();
        then(nodeServiceMock).should().getParentAssocs(categoryRootNodeRef);
        then(nodeServiceMock).shouldHaveNoMoreInteractions();
        then(categoryServiceMock).shouldHaveNoInteractions();
        then(authorityServiceMock).shouldHaveNoInteractions();
    }

    @Test
    public void testGetCategoryById_withChildren()
    {
        final NodeRef categoryNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, CATEGORY_ID);
        given(nodesMock.validateNode(CATEGORY_ID)).willReturn(categoryNodeRef);
        final Node categoryNode = new Node();
        categoryNode.setName(CATEGORY_NAME);
        categoryNode.setNodeId(CATEGORY_ID);
        final NodeRef parentNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, PARENT_ID);
        categoryNode.setParentId(parentNodeRef);
        given(nodesMock.getNode(CATEGORY_ID)).willReturn(categoryNode);
        final ChildAssociationRef parentAssoc = new ChildAssociationRef(null, parentNodeRef, null, categoryNodeRef);
        given(nodeServiceMock.getPrimaryParent(categoryNodeRef)).willReturn(parentAssoc);
        final List<ChildAssociationRef> dummyChildren = List.of(dummyChildAssociationRefMock);
        given(nodeServiceMock.getChildAssocs(categoryNodeRef, RegexQNamePattern.MATCH_ALL, RegexQNamePattern.MATCH_ALL, false))
                .willReturn(dummyChildren);
        //when
        final Category category = objectUnderTest.getCategoryById(CATEGORY_ID, parametersMock);

        then(nodesMock).should().validateNode(CATEGORY_ID);
        then(nodesMock).should().isSubClass(categoryNodeRef, ContentModel.TYPE_CATEGORY, false);
        then(nodesMock).should().getNode(CATEGORY_ID);
        then(nodesMock).shouldHaveNoMoreInteractions();

        then(nodeServiceMock).should().getPrimaryParent(categoryNodeRef);
        then(nodeServiceMock).should().getParentAssocs(parentNodeRef);
        then(nodeServiceMock).should().getParentAssocs(categoryNodeRef);
        then(nodeServiceMock).should().getChildAssocs(categoryNodeRef, RegexQNamePattern.MATCH_ALL, RegexQNamePattern.MATCH_ALL, false);
        then(nodeServiceMock).shouldHaveNoMoreInteractions();

        then(categoryServiceMock).shouldHaveNoInteractions();
        then(authorityServiceMock).shouldHaveNoInteractions();

        final Category expectedCategory = Category.builder()
                .id(CATEGORY_ID)
                .name(categoryNode.getName())
                .hasChildren(true)
                .parentId(PARENT_ID)
                .create();
        assertEquals(expectedCategory, category);
    }

    @Test
    public void testGetCategoryById_withoutChildren()
    {
        final NodeRef categoryNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, CATEGORY_ID);
        given(nodesMock.validateNode(CATEGORY_ID)).willReturn(categoryNodeRef);
        final Node categoryNode = new Node();
        categoryNode.setName(CATEGORY_NAME);
        categoryNode.setNodeId(CATEGORY_ID);
        final NodeRef parentNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, PARENT_ID);
        categoryNode.setParentId(parentNodeRef);
        given(nodesMock.getNode(CATEGORY_ID)).willReturn(categoryNode);
        final ChildAssociationRef parentAssoc = new ChildAssociationRef(null, parentNodeRef, null, categoryNodeRef);
        given(nodeServiceMock.getPrimaryParent(categoryNodeRef)).willReturn(parentAssoc);
        given(nodeServiceMock.getChildAssocs(categoryNodeRef, RegexQNamePattern.MATCH_ALL, RegexQNamePattern.MATCH_ALL, false))
                .willReturn(Collections.emptyList());
        //when
        final Category category = objectUnderTest.getCategoryById(CATEGORY_ID, parametersMock);

        then(nodesMock).should().validateNode(CATEGORY_ID);
        then(nodesMock).should().isSubClass(categoryNodeRef, ContentModel.TYPE_CATEGORY, false);
        then(nodesMock).should().getNode(CATEGORY_ID);
        then(nodesMock).shouldHaveNoMoreInteractions();

        then(nodeServiceMock).should().getPrimaryParent(categoryNodeRef);
        then(nodeServiceMock).should().getParentAssocs(parentNodeRef);
        then(nodeServiceMock).should().getParentAssocs(categoryNodeRef);
        then(nodeServiceMock).should().getChildAssocs(categoryNodeRef, RegexQNamePattern.MATCH_ALL, RegexQNamePattern.MATCH_ALL, false);
        then(nodeServiceMock).shouldHaveNoMoreInteractions();

        then(categoryServiceMock).shouldHaveNoInteractions();
        then(authorityServiceMock).shouldHaveNoInteractions();

        final Category expectedCategory = Category.builder()
                .id(CATEGORY_ID)
                .name(categoryNode.getName())
                .hasChildren(false)
                .parentId(PARENT_ID)
                .create();
        assertEquals(expectedCategory, category);
    }

    @Test
    public void testGetCategoryById_notACategory()
    {
        final NodeRef categoryNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, CATEGORY_ID);
        given(nodesMock.validateNode(CATEGORY_ID)).willReturn(categoryNodeRef);
        given(nodesMock.isSubClass(categoryNodeRef, ContentModel.TYPE_CATEGORY, false)).willReturn(false);

        //when
        assertThrows(InvalidArgumentException.class, () -> objectUnderTest.getCategoryById(CATEGORY_ID, parametersMock));

        then(nodesMock).should().validateNode(CATEGORY_ID);
        then(nodesMock).should().isSubClass(categoryNodeRef, ContentModel.TYPE_CATEGORY, false);
        then(nodesMock).shouldHaveNoMoreInteractions();

        then(nodeServiceMock).shouldHaveNoInteractions();
        then(categoryServiceMock).shouldHaveNoInteractions();
        then(authorityServiceMock).shouldHaveNoInteractions();
    }

    @Test
    public void testGetCategoryById_nodeNotExists()
    {
        given(nodesMock.validateNode(CATEGORY_ID)).willThrow(EntityNotFoundException.class);

        //when
        assertThrows(EntityNotFoundException.class, () -> objectUnderTest.getCategoryById(CATEGORY_ID, parametersMock));

        then(nodesMock).should().validateNode(CATEGORY_ID);
        then(nodesMock).shouldHaveNoMoreInteractions();

        then(nodeServiceMock).shouldHaveNoInteractions();
        then(categoryServiceMock).shouldHaveNoInteractions();
        then(authorityServiceMock).shouldHaveNoInteractions();
    }

    @Test
    public void testDeleteCategoryById_asAdmin()
    {
        given(authorityServiceMock.hasAdminAuthority()).willReturn(true);
        final NodeRef categoryNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, CATEGORY_ID);
        given(nodesMock.validateNode(CATEGORY_ID)).willReturn(categoryNodeRef);
        given(nodesMock.isSubClass(categoryNodeRef, ContentModel.TYPE_CATEGORY, false)).willReturn(true);
        final Node categoryNode = new Node();
        categoryNode.setName(CATEGORY_NAME);
        categoryNode.setNodeId(CATEGORY_ID);
        final NodeRef parentNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, PARENT_ID);
        categoryNode.setParentId(parentNodeRef);

        //when
        objectUnderTest.deleteCategoryById(CATEGORY_ID, parametersMock);

        then(authorityServiceMock).should().hasAdminAuthority();
        then(authorityServiceMock).shouldHaveNoMoreInteractions();
        then(nodesMock).should().validateNode(CATEGORY_ID);

        then(nodesMock).should().isSubClass(categoryNodeRef, ContentModel.TYPE_CATEGORY, false);
        then(nodesMock).shouldHaveNoMoreInteractions();

        then(nodeServiceMock).should().getParentAssocs(categoryNodeRef);
        then(nodeServiceMock).should().deleteNode(categoryNodeRef);
        then(nodeServiceMock).shouldHaveNoMoreInteractions();
    }

    @Test
    public void testDeleteCategoryById_asNonAdminUser()
    {
        given(authorityServiceMock.hasAdminAuthority()).willReturn(false);

        //when
        assertThrows(PermissionDeniedException.class, () -> objectUnderTest.deleteCategoryById(CATEGORY_ID, parametersMock));

        then(authorityServiceMock).should().hasAdminAuthority();
        then(authorityServiceMock).shouldHaveNoMoreInteractions();

        then(nodesMock).shouldHaveNoInteractions();
        then(nodeServiceMock).shouldHaveNoInteractions();
    }

    @Test
    public void testDeleteCategoryById_nonCategoryId()
    {
        given(authorityServiceMock.hasAdminAuthority()).willReturn(true);
        final NodeRef categoryNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, CATEGORY_ID);
        given(nodesMock.validateNode(CATEGORY_ID)).willReturn(categoryNodeRef);
        given(nodesMock.isSubClass(categoryNodeRef, ContentModel.TYPE_CATEGORY, false)).willReturn(false);

        //when
        assertThrows(InvalidArgumentException.class, () -> objectUnderTest.deleteCategoryById(CATEGORY_ID, parametersMock));

        then(authorityServiceMock).should().hasAdminAuthority();
        then(authorityServiceMock).shouldHaveNoMoreInteractions();

        then(nodesMock).should().validateNode(CATEGORY_ID);
        then(nodesMock).should().isSubClass(categoryNodeRef, ContentModel.TYPE_CATEGORY, false);
        then(nodesMock).shouldHaveNoMoreInteractions();
    }

    @Test
    public void testDeleteCategoryById_rootCategory()
    {
        given(authorityServiceMock.hasAdminAuthority()).willReturn(true);
        final NodeRef categoryRootNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, CAT_ROOT_NODE_ID);
        given(nodesMock.validateNode(CAT_ROOT_NODE_ID)).willReturn(categoryRootNodeRef);
        given(nodesMock.isSubClass(categoryRootNodeRef, ContentModel.TYPE_CATEGORY, false)).willReturn(true);
        given(categoryChildAssociationRefMock.getQName()).willReturn(ContentModel.ASPECT_GEN_CLASSIFIABLE);
        given(nodeServiceMock.getParentAssocs(categoryRootNodeRef)).willReturn(List.of(categoryChildAssociationRefMock));

        //when
        assertThrows(InvalidArgumentException.class, () -> objectUnderTest.deleteCategoryById(CAT_ROOT_NODE_ID, parametersMock));

        then(authorityServiceMock).should().hasAdminAuthority();
        then(authorityServiceMock).shouldHaveNoMoreInteractions();

        then(nodesMock).should().validateNode(CAT_ROOT_NODE_ID);
        then(nodesMock).should().isSubClass(categoryRootNodeRef, ContentModel.TYPE_CATEGORY, false);
        then(nodesMock).shouldHaveNoMoreInteractions();

        then(nodeServiceMock).should().getParentAssocs(categoryRootNodeRef);
        then(nodeServiceMock).shouldHaveNoMoreInteractions();
    }

    @Test
    public void testCreateCategoryUnderRoot()
    {
        final NodeRef parentCategoryNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, PATH_ROOT);
        given(categoryServiceMock.getRootCategoryNodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE))
                .willReturn(Optional.of(parentCategoryNodeRef));
        final NodeRef categoryNodeRef = createNodeRefWithId(CATEGORY_ID);
        given(categoryServiceMock.createCategory(parentCategoryNodeRef, CATEGORY_NAME)).willReturn(categoryNodeRef);
        given(nodesMock.getNode(CATEGORY_ID)).willReturn(prepareCategoryNode());
        final ChildAssociationRef parentAssoc = new ChildAssociationRef(null, parentCategoryNodeRef, null, categoryNodeRef);
        given(nodeServiceMock.getPrimaryParent(categoryNodeRef)).willReturn(parentAssoc);
        given(nodeServiceMock.getParentAssocs(parentCategoryNodeRef)).willReturn(List.of(parentAssoc));
        given(nodeServiceMock.getChildAssocs(categoryNodeRef, RegexQNamePattern.MATCH_ALL, RegexQNamePattern.MATCH_ALL, false))
                .willReturn(Collections.emptyList());

        //when
        final List<Category> createdCategories = objectUnderTest.createSubcategories(PATH_ROOT, prepareCategories(), parametersMock);

        then(authorityServiceMock).should().hasAdminAuthority();
        then(authorityServiceMock).shouldHaveNoMoreInteractions();
        then(nodesMock).should().getNode(CATEGORY_ID);
        then(nodesMock).shouldHaveNoMoreInteractions();
        then(nodeServiceMock).should().getPrimaryParent(categoryNodeRef);
        then(nodeServiceMock).should().getParentAssocs(parentCategoryNodeRef);
        then(nodeServiceMock).should().getChildAssocs(categoryNodeRef, RegexQNamePattern.MATCH_ALL, RegexQNamePattern.MATCH_ALL, false);
        then(nodeServiceMock).shouldHaveNoMoreInteractions();
        then(categoryServiceMock).should().getRootCategoryNodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
        then(categoryServiceMock).should().createCategory(parentCategoryNodeRef, CATEGORY_NAME);
        then(categoryServiceMock).shouldHaveNoMoreInteractions();

        assertEquals(1, createdCategories.size());
        final Category expectedCategory = Category.builder()
                .id(CATEGORY_ID)
                .name(CATEGORY_NAME)
                .hasChildren(false)
                .parentId(PATH_ROOT)
                .create();
        final Category createdCategory = createdCategories.iterator().next();
        assertEquals(expectedCategory, createdCategory);
    }

    @Test
    public void testCreateCategory()
    {
        final NodeRef parentCategoryNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, PARENT_ID);
        given(nodesMock.validateNode(PARENT_ID)).willReturn(parentCategoryNodeRef);
        final NodeRef categoryNodeRef = createNodeRefWithId(CATEGORY_ID);
        given(categoryServiceMock.createCategory(parentCategoryNodeRef, CATEGORY_NAME)).willReturn(categoryNodeRef);
        given(nodesMock.getNode(CATEGORY_ID)).willReturn(prepareCategoryNode());
        final ChildAssociationRef parentAssoc = new ChildAssociationRef(null, parentCategoryNodeRef, null, categoryNodeRef);
        given(nodeServiceMock.getPrimaryParent(categoryNodeRef)).willReturn(parentAssoc);
        given(nodeServiceMock.getParentAssocs(parentCategoryNodeRef)).willReturn(List.of(parentAssoc));
        given(nodeServiceMock.getChildAssocs(categoryNodeRef, RegexQNamePattern.MATCH_ALL, RegexQNamePattern.MATCH_ALL, false))
                .willReturn(Collections.emptyList());

        //when
        final List<Category> createdCategories = objectUnderTest.createSubcategories(PARENT_ID, prepareCategories(), parametersMock);

        then(authorityServiceMock).should().hasAdminAuthority();
        then(authorityServiceMock).shouldHaveNoMoreInteractions();
        then(nodesMock).should().validateNode(PARENT_ID);
        then(nodesMock).should().isSubClass(parentCategoryNodeRef, ContentModel.TYPE_CATEGORY, false);
        then(nodesMock).should().getNode(CATEGORY_ID);
        then(nodesMock).shouldHaveNoMoreInteractions();
        then(nodeServiceMock).should().getPrimaryParent(categoryNodeRef);
        then(nodeServiceMock).should().getParentAssocs(parentCategoryNodeRef);
        then(nodeServiceMock).should().getChildAssocs(categoryNodeRef, RegexQNamePattern.MATCH_ALL, RegexQNamePattern.MATCH_ALL, false);
        then(nodeServiceMock).shouldHaveNoMoreInteractions();
        then(categoryServiceMock).should().createCategory(parentCategoryNodeRef, CATEGORY_NAME);
        then(categoryServiceMock).shouldHaveNoMoreInteractions();

        assertEquals(1, createdCategories.size());
        final Category expectedCategory = Category.builder()
                .id(CATEGORY_ID)
                .name(CATEGORY_NAME)
                .hasChildren(false)
                .parentId(PARENT_ID)
                .create();
        final Category createdCategory = createdCategories.iterator().next();
        assertEquals(expectedCategory, createdCategory);
    }

    @Test
    public void testCreateCategories_noPermissions()
    {
        given(authorityServiceMock.hasAdminAuthority()).willReturn(false);

        //when
        assertThrows(PermissionDeniedException.class,
                () -> objectUnderTest.createSubcategories(PARENT_ID, prepareCategories(), parametersMock));

        then(authorityServiceMock).should().hasAdminAuthority();
        then(authorityServiceMock).shouldHaveNoMoreInteractions();
        then(nodesMock).shouldHaveNoInteractions();
        then(nodeServiceMock).shouldHaveNoInteractions();
        then(categoryServiceMock).shouldHaveNoInteractions();
    }

    @Test
    public void testCreateCategories_wrongParentNodeType()
    {
        final NodeRef parentCategoryNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, PARENT_ID);
        given(nodesMock.validateNode(PARENT_ID)).willReturn(parentCategoryNodeRef);
        given(nodesMock.isSubClass(parentCategoryNodeRef, ContentModel.TYPE_CATEGORY, false)).willReturn(false);

        //when
        assertThrows(InvalidArgumentException.class,
                () -> objectUnderTest.createSubcategories(PARENT_ID, prepareCategories(), parametersMock));

        then(authorityServiceMock).should().hasAdminAuthority();
        then(authorityServiceMock).shouldHaveNoMoreInteractions();
        then(nodesMock).should().validateNode(PARENT_ID);
        then(nodesMock).should().isSubClass(parentCategoryNodeRef, ContentModel.TYPE_CATEGORY, false);
        then(nodesMock).shouldHaveNoMoreInteractions();
        then(nodeServiceMock).shouldHaveNoInteractions();
        then(categoryServiceMock).shouldHaveNoInteractions();
    }

    @Test
    public void testCreateCategories_nonExistingParentNode()
    {
        given(nodesMock.validateNode(PARENT_ID)).willThrow(EntityNotFoundException.class);

        //when
        assertThrows(EntityNotFoundException.class,
                () -> objectUnderTest.createSubcategories(PARENT_ID, prepareCategories(), parametersMock));

        then(authorityServiceMock).should().hasAdminAuthority();
        then(authorityServiceMock).shouldHaveNoMoreInteractions();
        then(nodesMock).should().validateNode(PARENT_ID);
        then(nodesMock).shouldHaveNoMoreInteractions();
        then(nodeServiceMock).shouldHaveNoInteractions();
        then(categoryServiceMock).shouldHaveNoInteractions();
    }

    @Test
    public void testGetRootCategoryChildren()
    {
        final NodeRef parentCategoryNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, PATH_ROOT);
        given(categoryServiceMock.getRootCategoryNodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE))
                .willReturn(Optional.of(parentCategoryNodeRef));
        final int childrenCount = 3;
        final List<ChildAssociationRef> childAssociationRefMocks = prepareChildAssocMocks(childrenCount, parentCategoryNodeRef);
        given(nodeServiceMock.getChildAssocs(parentCategoryNodeRef)).willReturn(childAssociationRefMocks);
        childAssociationRefMocks.forEach(this::prepareCategoryNodeMocks);

        //when
        final CollectionWithPagingInfo<Category> categoryChildren = objectUnderTest.getCategoryChildren(PATH_ROOT, parametersMock);

        then(categoryServiceMock).should().getRootCategoryNodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
        then(categoryServiceMock).shouldHaveNoMoreInteractions();
        then(nodeServiceMock).should().getChildAssocs(parentCategoryNodeRef);
        childAssociationRefMocks.forEach(ca -> {
            then(nodesMock).should().getNode(ca.getChildRef().getId());
            then(nodeServiceMock).should()
                    .getChildAssocs(ca.getChildRef(), RegexQNamePattern.MATCH_ALL, RegexQNamePattern.MATCH_ALL, false);
            then(nodeServiceMock).should().getPrimaryParent(ca.getChildRef());
        });
        then(nodeServiceMock).should(times(childrenCount)).getParentAssocs(parentCategoryNodeRef);

        then(nodesMock).shouldHaveNoMoreInteractions();
        then(nodeServiceMock).shouldHaveNoMoreInteractions();

        then(authorityServiceMock).shouldHaveNoInteractions();

        assertEquals(childAssociationRefMocks.size(), categoryChildren.getTotalItems().intValue());
        final List<Category> categoryChildrenList = new ArrayList<>(categoryChildren.getCollection());
        assertEquals(childAssociationRefMocks.size(), categoryChildrenList.size());
        IntStream.range(0, childrenCount).forEach(i -> doCategoryAssertions(categoryChildrenList.get(i), i, PATH_ROOT));
    }

    @Test
    public void testGetCategoryChildren()
    {
        final NodeRef parentCategoryNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, PARENT_ID);
        given(nodesMock.validateNode(PARENT_ID)).willReturn(parentCategoryNodeRef);
        given(nodesMock.isSubClass(parentCategoryNodeRef, ContentModel.TYPE_CATEGORY, false)).willReturn(true);
        final int childrenCount = 3;
        final List<ChildAssociationRef> childAssociationRefMocks = prepareChildAssocMocks(childrenCount, parentCategoryNodeRef);
        given(nodeServiceMock.getChildAssocs(parentCategoryNodeRef)).willReturn(childAssociationRefMocks);
        childAssociationRefMocks.forEach(this::prepareCategoryNodeMocks);

        //when
        final CollectionWithPagingInfo<Category> categoryChildren = objectUnderTest.getCategoryChildren(PARENT_ID, parametersMock);

        then(nodesMock).should().validateNode(PARENT_ID);
        then(nodesMock).should().isSubClass(parentCategoryNodeRef, ContentModel.TYPE_CATEGORY, false);
        then(nodeServiceMock).should().getChildAssocs(parentCategoryNodeRef);
        childAssociationRefMocks.forEach(ca -> {
            then(nodesMock).should().getNode(ca.getChildRef().getId());
            then(nodeServiceMock).should()
                    .getChildAssocs(ca.getChildRef(), RegexQNamePattern.MATCH_ALL, RegexQNamePattern.MATCH_ALL, false);
            then(nodeServiceMock).should().getPrimaryParent(ca.getChildRef());
        });
        then(nodeServiceMock).should(times(childrenCount)).getParentAssocs(parentCategoryNodeRef);

        then(nodesMock).shouldHaveNoMoreInteractions();
        then(nodeServiceMock).shouldHaveNoMoreInteractions();

        then(authorityServiceMock).shouldHaveNoInteractions();
        then(categoryServiceMock).shouldHaveNoInteractions();

        assertEquals(childAssociationRefMocks.size(), categoryChildren.getTotalItems().intValue());
        final List<Category> categoryChildrenList = new ArrayList<>(categoryChildren.getCollection());
        assertEquals(childAssociationRefMocks.size(), categoryChildrenList.size());
        IntStream.range(0, childrenCount).forEach(i -> doCategoryAssertions(categoryChildrenList.get(i), i, PARENT_ID));
    }

    @Test
    public void testGetCategoryChildren_noChildren()
    {
        final NodeRef parentCategoryNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, PARENT_ID);
        given(nodesMock.validateNode(PARENT_ID)).willReturn(parentCategoryNodeRef);
        given(nodesMock.isSubClass(parentCategoryNodeRef, ContentModel.TYPE_CATEGORY, false)).willReturn(true);

        given(nodeServiceMock.getChildAssocs(parentCategoryNodeRef)).willReturn(Collections.emptyList());


        //when
        final CollectionWithPagingInfo<Category> categoryChildren = objectUnderTest.getCategoryChildren(PARENT_ID, parametersMock);

        then(nodesMock).should().validateNode(PARENT_ID);
        then(nodesMock).should().isSubClass(parentCategoryNodeRef, ContentModel.TYPE_CATEGORY, false);
        then(nodesMock).shouldHaveNoMoreInteractions();
        then(nodeServiceMock).should().getChildAssocs(parentCategoryNodeRef);
        then(nodeServiceMock).shouldHaveNoMoreInteractions();

        then(authorityServiceMock).shouldHaveNoInteractions();
        then(categoryServiceMock).shouldHaveNoInteractions();

        assertEquals(0, categoryChildren.getTotalItems().intValue());
    }

    @Test
    public void testGetCategoryChildren_wrongParentNodeType()
    {
        final NodeRef parentCategoryNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, PARENT_ID);
        given(nodesMock.validateNode(PARENT_ID)).willReturn(parentCategoryNodeRef);
        given(nodesMock.isSubClass(parentCategoryNodeRef, ContentModel.TYPE_CATEGORY, false)).willReturn(false);

        //when
        assertThrows(InvalidArgumentException.class, () -> objectUnderTest.getCategoryChildren(PARENT_ID, parametersMock));

        then(nodesMock).should().validateNode(PARENT_ID);
        then(nodesMock).should().isSubClass(parentCategoryNodeRef, ContentModel.TYPE_CATEGORY, false);
        then(nodesMock).shouldHaveNoMoreInteractions();

        then(nodeServiceMock).shouldHaveNoInteractions();
        then(categoryServiceMock).shouldHaveNoInteractions();
        then(authorityServiceMock).shouldHaveNoInteractions();
    }

    @Test
    public void testGetCategoryChildren_nonExistingParentNode()
    {
        given(nodesMock.validateNode(PARENT_ID)).willThrow(EntityNotFoundException.class);

        //when
        assertThrows(EntityNotFoundException.class, () -> objectUnderTest.getCategoryChildren(PARENT_ID, parametersMock));


        then(nodesMock).should().validateNode(PARENT_ID);
        then(nodesMock).shouldHaveNoMoreInteractions();

        then(nodeServiceMock).shouldHaveNoInteractions();
        then(categoryServiceMock).shouldHaveNoInteractions();
        then(authorityServiceMock).shouldHaveNoInteractions();
    }

    @Test
    public void testUpdateCategoryById()
    {
        final String categoryNewName = "categoryNewName";
        final Category fixedCategory = createCategoryOnlyWithName(categoryNewName);
        final QName categoryQName = createCmQNameOf(CATEGORY_NAME);
        final NodeRef parentCategoryNodeRef = createNodeRefWithId(PARENT_ID);
        final ChildAssociationRef parentAssociation = createAssociationOf(parentCategoryNodeRef, CATEGORY_NODE_REF, categoryQName);
        given(nodesMock.getNode(any())).willReturn(prepareCategoryNode(categoryNewName));
        given(nodeServiceMock.getPrimaryParent(any())).willReturn(parentAssociation);
        given(nodeServiceMock.moveNode(any(), any(), any(), any())).willReturn(createAssociationOf(parentCategoryNodeRef, CATEGORY_NODE_REF, createCmQNameOf(categoryNewName)));

        // when
        final Category actualCategory = objectUnderTest.updateCategoryById(CATEGORY_ID, fixedCategory);

        then(authorityServiceMock).should().hasAdminAuthority();
        then(authorityServiceMock).shouldHaveNoMoreInteractions();
        then(nodesMock).should().validateNode(CATEGORY_ID);
        then(nodesMock).should().isSubClass(CATEGORY_NODE_REF, ContentModel.TYPE_CATEGORY, false);
        then(nodesMock).should().getNode(CATEGORY_ID);
        then(nodesMock).shouldHaveNoMoreInteractions();
        then(nodeServiceMock).should().getParentAssocs(CATEGORY_NODE_REF);
        then(nodeServiceMock).should().getChildAssocs(CATEGORY_NODE_REF, RegexQNamePattern.MATCH_ALL, RegexQNamePattern.MATCH_ALL, false);
        then(nodeServiceMock).should().setProperty(CATEGORY_NODE_REF, ContentModel.PROP_NAME, categoryNewName);
        then(nodeServiceMock).should(times(2)).getPrimaryParent(CATEGORY_NODE_REF);
        final QName expectedNewQName = createCmQNameOf(categoryNewName);
        then(nodeServiceMock).should().moveNode(CATEGORY_NODE_REF, parentCategoryNodeRef, ContentModel.ASSOC_SUBCATEGORIES, expectedNewQName);
        then(nodeServiceMock).should().getParentAssocs(parentCategoryNodeRef);
        then(nodeServiceMock).shouldHaveNoMoreInteractions();
        then(categoryServiceMock).shouldHaveNoInteractions();
        final Category expectedCategory = createDefaultCategoryWithName(categoryNewName);
        assertThat(actualCategory)
            .isNotNull().usingRecursiveComparison()
            .isEqualTo(expectedCategory);
    }

    @Test
    public void testUpdateCategoryById_noPermission()
    {
        given(authorityServiceMock.hasAdminAuthority()).willReturn(false);

        // when
        assertThatExceptionOfType(PermissionDeniedException.class).isThrownBy(() -> objectUnderTest.updateCategoryById(CATEGORY_ID, CATEGORY));

        then(nodesMock).shouldHaveNoInteractions();
        then(nodeServiceMock).shouldHaveNoInteractions();
    }

    @Test
    public void testUpdateCategoryById_categoryNodeNotFound()
    {
        given(nodesMock.validateNode(any(String.class))).willThrow(EntityNotFoundException.class);

        // when
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> objectUnderTest.updateCategoryById(CATEGORY_ID, CATEGORY));

        then(nodeServiceMock).shouldHaveNoInteractions();
    }

    @Test
    public void testUpdateCategoryById_notACategory()
    {
        given(nodesMock.isSubClass(any(), any(), eq(false))).willReturn(false);

        // when
        assertThatExceptionOfType(InvalidArgumentException.class).isThrownBy(() -> objectUnderTest.updateCategoryById(CATEGORY_ID, CATEGORY))
            .withMessageContaining(NOT_A_VALID_CATEGORY);

        then(nodeServiceMock).shouldHaveNoInteractions();
    }

    @Test
    public void testUpdateCategoryById_isRootCategory()
    {
        given(categoryServiceMock.getRootCategoryNodeRef(any())).willReturn(Optional.of(createNodeRefWithId(PATH_ROOT)));
        given(nodeServiceMock.getParentAssocs(any())).willReturn(List.of(categoryChildAssociationRefMock));
        given(categoryChildAssociationRefMock.getQName()).willReturn(ContentModel.ASPECT_GEN_CLASSIFIABLE);

        // when
        assertThatExceptionOfType(InvalidArgumentException.class).isThrownBy(() -> objectUnderTest.updateCategoryById(PATH_ROOT, CATEGORY))
            .withMessageContaining(NOT_A_VALID_CATEGORY);

        then(categoryServiceMock).should().getRootCategoryNodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
        then(categoryServiceMock).shouldHaveNoMoreInteractions();
    }

    private List<String> getInvalidCategoryNames()
    {
        final List<String> invalidNames = new ArrayList<>();
        invalidNames.add(null);
        invalidNames.add("");
        return invalidNames;
    }

    @Test
    public void testUpdateCategoryById_emptyName()
    {
        for (String invalidName : getInvalidCategoryNames())
        {
            final Category categoryWithoutName = createCategoryOnlyWithName(invalidName);

            // when
            assertThatExceptionOfType(InvalidArgumentException.class).isThrownBy(() -> objectUnderTest.updateCategoryById(CATEGORY_ID, categoryWithoutName))
                .withMessageContaining(NOT_NULL_OR_EMPTY);
        }
    }

    @Test
    public void testUpdateCategoryById_notMatchingIdField()
    {
        final String categoryNewName = "categoryNewName";
        final Category categoryWithInvalidId = createCategoryOnlyWithName(categoryNewName);
        categoryWithInvalidId.setId("different-" + CATEGORY_ID);
        final QName categoryQName = createCmQNameOf(CATEGORY_NAME);
        final NodeRef parentCategoryNodeRef = createNodeRefWithId(PARENT_ID);
        final ChildAssociationRef parentAssociation = createAssociationOf(parentCategoryNodeRef, CATEGORY_NODE_REF, categoryQName);
        given(nodesMock.getNode(any())).willReturn(prepareCategoryNode(categoryNewName));
        given(nodeServiceMock.getPrimaryParent(any())).willReturn(parentAssociation);
        given(nodeServiceMock.moveNode(any(), any(), any(), any())).willReturn(createAssociationOf(parentCategoryNodeRef, CATEGORY_NODE_REF, createCmQNameOf(categoryNewName)));

        // when
        final Category actualCategory = objectUnderTest.updateCategoryById(CATEGORY_ID, categoryWithInvalidId);

        final Category expectedCategory = createDefaultCategoryWithName(categoryNewName);
        assertThat(actualCategory)
            .isNotNull().usingRecursiveComparison()
            .isEqualTo(expectedCategory);
    }

    @Test
    public void testUpdateCategoryById_notMatchingParentIdField()
    {
        final String categoryNewName = "categoryNewName";
        final Category categoryWithInvalidParentId = createCategoryOnlyWithName(categoryNewName);
        categoryWithInvalidParentId.setParentId("different-" + PARENT_ID);
        final QName categoryQName = createCmQNameOf(CATEGORY_NAME);
        final NodeRef parentCategoryNodeRef = createNodeRefWithId(PARENT_ID);
        final ChildAssociationRef parentAssociation = createAssociationOf(parentCategoryNodeRef, CATEGORY_NODE_REF, categoryQName);
        given(nodesMock.getNode(any())).willReturn(prepareCategoryNode(categoryNewName));
        given(nodeServiceMock.getPrimaryParent(any())).willReturn(parentAssociation);
        given(nodeServiceMock.moveNode(any(), any(), any(), any())).willReturn(createAssociationOf(parentCategoryNodeRef, CATEGORY_NODE_REF, createCmQNameOf(categoryNewName)));

        // when
        final Category actualCategory = objectUnderTest.updateCategoryById(CATEGORY_ID, categoryWithInvalidParentId);

        final Category expectedCategory = createDefaultCategoryWithName(categoryNewName);
        assertThat(actualCategory)
            .isNotNull().usingRecursiveComparison()
            .isEqualTo(expectedCategory);
    }

    @Test
    public void testUpdateCategoryById_notMatchingHasChildrenField()
    {
        final String categoryNewName = "categoryNewName";
        final Category categoryWithInvalidHasChildren = createCategoryOnlyWithName(categoryNewName);
        categoryWithInvalidHasChildren.setHasChildren(true);
        final QName categoryQName = createCmQNameOf(CATEGORY_NAME);
        final NodeRef parentCategoryNodeRef = createNodeRefWithId(PARENT_ID);
        final ChildAssociationRef parentAssociation = createAssociationOf(parentCategoryNodeRef, CATEGORY_NODE_REF, categoryQName);
        given(nodesMock.getNode(any())).willReturn(prepareCategoryNode(categoryNewName));
        given(nodeServiceMock.getPrimaryParent(any())).willReturn(parentAssociation);
        given(nodeServiceMock.moveNode(any(), any(), any(), any())).willReturn(createAssociationOf(parentCategoryNodeRef, CATEGORY_NODE_REF, createCmQNameOf(categoryNewName)));

        // when
        final Category actualCategory = objectUnderTest.updateCategoryById(CATEGORY_ID, categoryWithInvalidHasChildren);

        final Category expectedCategory = createDefaultCategoryWithName(categoryNewName);
        assertThat(actualCategory)
            .isNotNull().usingRecursiveComparison()
            .isEqualTo(expectedCategory);
    }

    private Node prepareCategoryNode(final String name, final String id, final NodeRef parentNodeRef)
    {
        final Node categoryNode = new Node();
        categoryNode.setName(name);
        categoryNode.setNodeId(id);
        categoryNode.setParentId(parentNodeRef);
        return categoryNode;
    }

    private Node prepareCategoryNode(final String name)
    {
        final NodeRef parentNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, PARENT_ID);
        return prepareCategoryNode(name, CATEGORY_ID, parentNodeRef);
    }

    private Node prepareCategoryNode()
    {
        return prepareCategoryNode(CATEGORY_NAME);
    }

    private List<Category> prepareCategories()
    {
        return List.of(Category.builder()
                .name(CATEGORY_NAME)
                .create());
    }

    private List<ChildAssociationRef> prepareChildAssocMocks(final int count, NodeRef parentCategoryNodeRef)
    {
        return IntStream.range(0, count).mapToObj(i -> {
            ChildAssociationRef dummyChildAssocMock = mock(ChildAssociationRef.class);
            given(dummyChildAssocMock.getTypeQName()).willReturn(ContentModel.ASSOC_SUBCATEGORIES);
            given(dummyChildAssocMock.getChildRef())
                    .willReturn(new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, CATEGORY_ID + "-" + i));
            given(dummyChildAssocMock.getParentRef()).willReturn(parentCategoryNodeRef);
            return dummyChildAssocMock;
        }).collect(Collectors.toList());
    }

    private void prepareCategoryNodeMocks(ChildAssociationRef childAssociationRef)
    {
        final NodeRef childRef = childAssociationRef.getChildRef();
        final String id = childRef.getId();
        final String name = id.replace(CATEGORY_ID, CATEGORY_NAME);
        final NodeRef parentRef = childAssociationRef.getParentRef();
        given(nodesMock.getNode(id)).willReturn(prepareCategoryNode(name, id, parentRef));
        final ChildAssociationRef parentAssoc = new ChildAssociationRef(null, parentRef, null, childRef);
        given(nodeServiceMock.getPrimaryParent(childRef)).willReturn(parentAssoc);
        given(nodeServiceMock.getParentAssocs(parentRef)).willReturn(List.of(parentAssoc));
    }

    private void doCategoryAssertions(final Category category, final int index, final String parentId)
    {
        final Category expectedCategory = Category.builder()
                .id(CATEGORY_ID + "-" + index)
                .name(CATEGORY_NAME + "-" + index)
                .parentId(parentId)
                .hasChildren(false)
                .create();
        assertEquals(expectedCategory, category);
    }

    private static NodeRef createNodeRefWithId(final String id)
    {
        return new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, id);
    }

    private static Category createCategoryOnlyWithName(final String name)
    {
        return Category.builder().name(name).create();
    }

    private static Category createDefaultCategoryWithName(final String name)
    {
        return Category.builder()
            .id(CATEGORY_ID)
            .name(name)
            .parentId(PARENT_ID)
            .hasChildren(false)
            .create();
    }

    private static QName createCmQNameOf(final String name)
    {
        return QName.createQName(ContentModel.TYPE_CATEGORY.getNamespaceURI(), QName.createValidLocalName(name));
    }

    private static ChildAssociationRef createAssociationOf(final NodeRef parentNode, final NodeRef childNode, final QName childNodeName)
    {
        return new ChildAssociationRef(ContentModel.ASSOC_SUBCATEGORIES, parentNode, childNodeName, childNode);
    }
}

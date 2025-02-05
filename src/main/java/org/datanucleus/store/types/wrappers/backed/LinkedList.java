/**********************************************************************
Copyright (c) 2003 Andy Jefferson and others. All rights reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Contributors:
    ...
**********************************************************************/
package org.datanucleus.store.types.wrappers.backed;

import java.io.ObjectStreamException;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.ExecutionContext;
import org.datanucleus.exceptions.NucleusDataStoreException;
import org.datanucleus.flush.ListAddAtOperation;
import org.datanucleus.flush.CollectionAddOperation;
import org.datanucleus.flush.CollectionClearOperation;
import org.datanucleus.flush.ListRemoveAtOperation;
import org.datanucleus.flush.CollectionRemoveOperation;
import org.datanucleus.flush.ListSetOperation;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.metadata.FieldPersistenceModifier;
import org.datanucleus.state.DNStateManager;
import org.datanucleus.store.BackedSCOStoreManager;
import org.datanucleus.store.types.SCOListIterator;
import org.datanucleus.store.types.SCOUtils;
import org.datanucleus.store.types.scostore.ListStore;
import org.datanucleus.store.types.scostore.Store;
import org.datanucleus.util.Localiser;
import org.datanucleus.util.NucleusLogger;

/**
 * A mutable second-class LinkedList object.
 * This class extends LinkedList, using that class to contain the current objects, and the backing ListStore 
 * to be the interface to the datastore. A "backing store" is not present for datastores that dont use
 * DatastoreClass, or if the container is serialised or non-persistent.
 * 
 * <H3>Modes of Operation</H3>
 * The user can operate the list in 2 modes.
 * The <B>cached</B> mode will use an internal cache of the elements (in the "delegate") reading them at
 * the first opportunity and then using the cache thereafter.
 * The <B>non-cached</B> mode will just go direct to the "backing store" each call.
 *
 * <H3>Mutators</H3>
 * When the "backing store" is present any updates are passed direct to the datastore as well as to the "delegate".
 * If the "backing store" isn't present the changes are made to the "delegate" only.
 *
 * <H3>Accessors</H3>
 * When any accessor method is invoked, it typically checks whether the container has been loaded from its
 * "backing store" (where present) and does this as necessary. Some methods (<B>size()</B>) just check if 
 * everything is loaded and use the delegate if possible, otherwise going direct to the datastore.
 */
public class LinkedList<E> extends org.datanucleus.store.types.wrappers.LinkedList<E> implements BackedSCO
{
    protected transient ListStore<E> backingStore;
    protected transient boolean allowNulls = false;
    protected transient boolean useCache=true;
    protected transient boolean isCacheLoaded=false;

    /**
     * Constructor, using StateManager of the "owner" and the field name.
     * @param ownerSM The owner StateManager
     * @param mmd Metadata for the member
     */
    public LinkedList(DNStateManager ownerSM, AbstractMemberMetaData mmd)
    {
        super(ownerSM, mmd);

        // Set up our delegate
        this.delegate = new java.util.LinkedList();

        allowNulls = SCOUtils.allowNullsInContainer(allowNulls, mmd);
        useCache = SCOUtils.useContainerCache(ownerSM, mmd);

        if (!SCOUtils.collectionHasSerialisedElements(mmd) && mmd.getPersistenceModifier() == FieldPersistenceModifier.PERSISTENT)
        {
            ClassLoaderResolver clr = ownerSM.getExecutionContext().getClassLoaderResolver();
            this.backingStore = (ListStore)((BackedSCOStoreManager)ownerSM.getStoreManager()).getBackingStoreForField(clr, mmd, java.util.LinkedList.class);
        }

        if (NucleusLogger.PERSISTENCE.isDebugEnabled())
        {
            NucleusLogger.PERSISTENCE.debug(SCOUtils.getContainerInfoMessage(ownerSM, ownerMmd.getName(), this,
                useCache, allowNulls, SCOUtils.useCachedLazyLoading(ownerSM, ownerMmd)));
        }
    }

    public void initialise(java.util.LinkedList newValue, Object oldValue)
    {
        if (newValue != null)
        {
            // Check for the case of serialised PC elements, and assign StateManagers to the elements without
            ExecutionContext ec = ownerSM.getExecutionContext();
            if (SCOUtils.collectionHasSerialisedElements(ownerMmd) && ownerMmd.getCollection().elementIsPersistent())
            {
                Iterator iter = newValue.iterator();
                while (iter.hasNext())
                {
                    Object pc = iter.next();
                    DNStateManager objSM = ec.findStateManager(pc);
                    if (objSM == null)
                    {
                        objSM = ec.getNucleusContext().getStateManagerFactory().newForEmbedded(ec, pc, false, ownerSM, ownerMmd.getAbsoluteFieldNumber());
                    }
                }
            }

            if (NucleusLogger.PERSISTENCE.isDebugEnabled())
            {
                NucleusLogger.PERSISTENCE.debug(Localiser.msg("023008", ownerSM.getObjectAsPrintable(), ownerMmd.getName(), "" + newValue.size()));
            }

            // TODO This does clear+addAll : Improve this and work out which elements are added and which deleted
            if (backingStore != null)
            {
                if (SCOUtils.useQueuedUpdate(ownerSM))
                {
                    if (ownerSM.isFlushedToDatastore() || !ownerSM.getLifecycleState().isNew())
                    {
                        ownerSM.getExecutionContext().addOperationToQueue(new CollectionClearOperation(ownerSM, backingStore));

                        for (Object element : newValue)
                        {
                            ownerSM.getExecutionContext().addOperationToQueue(new CollectionAddOperation(ownerSM, backingStore, element));
                        }
                    }
                }
                else
                {
                    backingStore.clear(ownerSM);

                    try
                    {
                        backingStore.addAll(ownerSM, newValue, useCache ? 0 : -1);
                    }
                    catch (NucleusDataStoreException dse)
                    {
                        NucleusLogger.PERSISTENCE.warn(Localiser.msg("023013", "addAll", ownerMmd.getName(), dse));
                    }
                }
            }
            delegate.addAll(newValue);
            isCacheLoaded = true;
            makeDirty();
        }
    }

    /**
     * Method to initialise the SCO from an existing value.
     * @param c The object to set from
     */
    public void initialise(java.util.LinkedList c)
    {
        if (c != null)
        {
            // Check for the case of serialised PC elements, and assign StateManagers to the elements without
            ExecutionContext ec = ownerSM.getExecutionContext();
            if (SCOUtils.collectionHasSerialisedElements(ownerMmd) && ownerMmd.getCollection().elementIsPersistent())
            {
                Iterator iter = c.iterator();
                while (iter.hasNext())
                {
                    Object pc = iter.next();
                    DNStateManager objSM = ec.findStateManager(pc);
                    if (objSM == null)
                    {
                        objSM = ec.getNucleusContext().getStateManagerFactory().newForEmbedded(ec, pc, false, ownerSM, ownerMmd.getAbsoluteFieldNumber());
                    }
                }
            }

            if (backingStore != null && useCache && !isCacheLoaded)
            {
                // Mark as loaded
                isCacheLoaded = true;
            }

            if (NucleusLogger.PERSISTENCE.isDebugEnabled())
            {
                NucleusLogger.PERSISTENCE.debug(Localiser.msg("023007", ownerSM.getObjectAsPrintable(), ownerMmd.getName(), "" + c.size()));
            }
            delegate.clear();
            delegate.addAll(c);
        }
    }

    /**
     * Method to initialise the SCO for use.
     */
    public void initialise()
    {
        if (useCache && !SCOUtils.useCachedLazyLoading(ownerSM, ownerMmd))
        {
            // Load up the container now if not using lazy loading
            loadFromStore();
        }
    }

    // ----------------------- Implementation of SCO methods -------------------

    /**
     * Accessor for the unwrapped value that we are wrapping.
     * @return The unwrapped value
     */
    public java.util.LinkedList getValue()
    {
        loadFromStore();
        return super.getValue();
    }

    /**
     * Method to effect the load of the data in the SCO.
     * Used when the SCO supports lazy-loading to tell it to load all now.
     */
    public void load()
    {
        if (useCache)
        {
            loadFromStore();
        }
    }

    /**
     * Method to return if the SCO has its contents loaded.
     * If the SCO doesn't support lazy loading will just return true.
     * @return Whether it is loaded
     */
    public boolean isLoaded()
    {
        return useCache ? isCacheLoaded : false;
    }

    /**
     * Method to load all elements from the "backing store" where appropriate.
     */
    protected void loadFromStore()
    {
        if (backingStore != null && !isCacheLoaded)
        {
            if (NucleusLogger.PERSISTENCE.isDebugEnabled())
            {
                NucleusLogger.PERSISTENCE.debug(Localiser.msg("023006", 
                    ownerSM.getObjectAsPrintable(), ownerMmd.getName()));
            }
            delegate.clear();
            Iterator<E> iter=backingStore.iterator(ownerSM);
            while (iter.hasNext())
            {
                delegate.add(iter.next());
            }

            isCacheLoaded = true;
        }
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.types.backed.BackedSCO#getBackingStore()
     */
    public Store getBackingStore()
    {
        return backingStore;
    }

    /**
     * Method to update an embedded element in this collection.
     * @param element The element
     * @param fieldNumber Number of field in the element
     * @param value New value for this field
     * @param makeDirty Whether to make the SCO field dirty.
     */
    public void updateEmbeddedElement(E element, int fieldNumber, Object value, boolean makeDirty)
    {
        if (backingStore != null)
        {
            backingStore.updateEmbeddedElement(ownerSM, element, fieldNumber, value);
        }
    }

    /**
     * Method to unset the owner and field information.
     */
    public void unsetOwner()
    {
        super.unsetOwner();
        if (backingStore != null)
        {
            backingStore = null;
        }
    }

    // ------------------- Implementation of LinkedList methods ----------------
 
    /**
     * Clone operator to return a copy of this object.
     * <P>Mutable second-class Objects are required to provide a public clone method in order to allow for copying persistable objects.
     * In contrast to Object.clone(), this method must not throw a CloneNotSupportedException.
     * </p>
     *
     * @return The cloned object
     */
    public Object clone()
    {
        if (useCache)
        {
            loadFromStore();
        }

        return delegate.clone();
    }

    /**
     * Method to return if the list contains this element.
     * @param element The element
     * @return Whether it is contained
     **/
    public boolean contains(Object element)
    {
        if (useCache && isCacheLoaded)
        {
            // If the "delegate" is already loaded, use it
            return delegate.contains(element);
        }
        else if (backingStore != null)
        {
            return backingStore.contains(ownerSM,element);
        }

        return delegate.contains(element);
    }

    /**
     * Accessor for whether a collection of elements are contained here.
     * @param c The collection of elements.
     * @return Whether they are contained.
     */
    public boolean containsAll(java.util.Collection c)
    {
        if (useCache)
        {
            loadFromStore();
        }
        else if (backingStore != null)
        {
            java.util.HashSet h=new java.util.HashSet(c);
            Iterator iter=iterator();
            while (iter.hasNext())
            {
                h.remove(iter.next());
            }

            return h.isEmpty();
        }

        return delegate.containsAll(c);
    }

    public boolean equals(Object o)
    {
        if (useCache)
        {
            loadFromStore();
        }

        if (o == this)
        {
            return true;
        }
        if (!(o instanceof java.util.List))
        {
            return false;
        }
        java.util.List l = (java.util.List)o;
        if (l.size() != size())
        {
            return false;
        }
        Object[] elements = toArray();
        Object[] otherElements = l.toArray();
        for (int i=0;i<elements.length;i++)
        {
            if (!elements[i].equals(otherElements[i]))
            {
                return false;
            }
        }
        return true;
    }

    @Override
    public void forEach(Consumer action)
    {
        Objects.requireNonNull(action);
        for (E t : this)
        { // uses iterator() implicitly
            action.accept(t);
        }
    }

    /**
     * Method to retrieve an element no.
     * @param index The item to retrieve
     * @return The element at that position.
     **/
    public E get(int index)
    {
        if (useCache)
        {
            loadFromStore();
        }
        else if (backingStore != null)
        {
            return backingStore.get(ownerSM, index);
        }

        return delegate.get(index);
    }

    /**
     * Method to retrieve the first element.
     * @return The first element
     **/
    public E getFirst()
    {
        return get(0);
    }

    /**
     * Method to retrieve the last element.
     * @return The last element
     **/
    public E getLast()
    {
        return get(size()-1);
    }

    public int hashCode()
    {
        if (useCache)
        {
            loadFromStore();
        }
        return delegate.hashCode();
    }

    /**
     * Method to the position of an element.
     * @param element The element.
     * @return The position.
     **/
    public int indexOf(Object element)
    {
        if (useCache)
        {
            loadFromStore();
        }
        else if (backingStore != null)
        {
            return backingStore.indexOf(ownerSM, element);
        }
 
        return delegate.indexOf(element);
    }

    /**
     * Accessor for whether the LinkedList is empty.
     * @return Whether it is empty.
     **/
    public boolean isEmpty()
    {
        return size() == 0;
    }

    /**
     * Method to retrieve an iterator for the list.
     * @return The iterator
     */
    public Iterator<E> iterator()
    {
        // Populate the cache if necessary
        if (useCache)
        {
            loadFromStore();
        }

        return new SCOListIterator(this, ownerSM, delegate, backingStore, useCache, -1);
    }

    /**
     * Method to retrieve a List iterator for the list from the index.
     * @param index The start point 
     * @return The iterator
     **/
    public ListIterator<E> listIterator(int index)
    {
        // Populate the cache if necessary
        if (useCache)
        {
            loadFromStore();
        }

        return new SCOListIterator(this, ownerSM, delegate, backingStore, useCache, index);
    }

    /**
     * Method to retrieve the last position of the element.
     * @param element The element
     * @return The last position of this element in the List.
     **/
    public int lastIndexOf(Object element)
    {
        if (useCache)
        {
            loadFromStore();
        }
        else if (backingStore != null)
        {
            return backingStore.lastIndexOf(ownerSM, element);
        }
 
        return delegate.lastIndexOf(element);
    }

    /**
     * Accessor for the size of the LinkedList.
     * @return The size.
     **/
    public int size()
    {
        if (useCache && isCacheLoaded)
        {
            // If the "delegate" is already loaded, use it
            return delegate.size();
        }
        else if (backingStore != null)
        {
            return backingStore.size(ownerSM);
        }

        return delegate.size();
    }

    /**
     * Accessor for the subList of elements between from and to of the List
     * @param from Start index (inclusive)
     * @param to End index (exclusive) 
     * @return The subList
     */
    public java.util.List<E> subList(int from,int to)
    {
        if (useCache)
        {
            loadFromStore();
        }
        else if (backingStore != null)
        {
            return backingStore.subList(ownerSM,from,to);
        }

        return delegate.subList(from,to);
    }

    /**
     * Method to return the list as an array.
     * @return The array
     */
    public Object[] toArray()
    {
        if (useCache)
        {
            loadFromStore();
        }
        else if (backingStore != null)
        {
            return SCOUtils.toArray(backingStore,ownerSM);
        }        
        return delegate.toArray();
    }

    /**
     * Method to return the list as an array.
     * @param a The runtime types of the array being defined by this param
     * @return The array
     */
    public Object[] toArray(Object a[])
    {
        if (useCache)
        {
            loadFromStore();
        }
        else if (backingStore != null)
        {
            return SCOUtils.toArray(backingStore,ownerSM,a);
        }        
        return delegate.toArray(a);
    }

    // ---------------------------- Mutator methods ----------------------------
 
    /**
     * Method to add an element to a position in the LinkedList.
     * @param index The position
     * @param element The new element
     **/
    public void add(int index, E element)
    {
        // Reject inappropriate elements
        if (!allowNulls && element == null)
        {
            throw new NullPointerException("Nulls not allowed for collection at field " + ownerMmd.getName() + " but element is null");
        }

        if (useCache)
        {
            loadFromStore();
        }

        if (backingStore != null)
        {
            if (SCOUtils.useQueuedUpdate(ownerSM))
            {
                ownerSM.getExecutionContext().addOperationToQueue(new ListAddAtOperation(ownerSM, backingStore, index, element));
            }
            else
            {
                try
                {
                    backingStore.add(ownerSM, element, index, useCache ? delegate.size() : -1);
                }
                catch (NucleusDataStoreException dse)
                {
                    NucleusLogger.PERSISTENCE.warn(Localiser.msg("023013", "add", ownerMmd.getName(), dse));
                }
            }
        }

        // Only make it dirty after adding the element(s) to the datastore so we give it time
        // to be inserted - otherwise jdoPreStore on this object would have been called before completing the addition
        makeDirty();

        delegate.add(index, element);

        if (ownerSM != null && !ownerSM.getExecutionContext().getTransaction().isActive())
        {
            ownerSM.getExecutionContext().processNontransactionalUpdate();
        }
    }

    /**
     * Method to add an element to the LinkedList.
     * @param element The new element
     * @return Whether it was added ok.
     **/
    public boolean add(E element)
    {
        // Reject inappropriate elements
        if (!allowNulls && element == null)
        {
            throw new NullPointerException("Nulls not allowed for collection at field " + ownerMmd.getName() + " but element is null");
        }

        if (useCache)
        {
            loadFromStore();
        }

        boolean backingSuccess = true;
        if (backingStore != null)
        {
            if (SCOUtils.useQueuedUpdate(ownerSM))
            {
                ownerSM.getExecutionContext().addOperationToQueue(new CollectionAddOperation(ownerSM, backingStore, element));
            }
            else
            {
                try
                {
                    backingSuccess = backingStore.add(ownerSM, element, useCache ? delegate.size() : -1);
                }
                catch (NucleusDataStoreException dse)
                {
                    NucleusLogger.PERSISTENCE.warn(Localiser.msg("023013", "add", ownerMmd.getName(), dse));
                    backingSuccess = false;
                }
            }
        }

        // Only make it dirty after adding the element(s) to the datastore so we give it time
        // to be inserted - otherwise jdoPreStore on this object would have been called before completing the addition
        makeDirty();

        boolean delegateSuccess = delegate.add(element);

        if (ownerSM != null && !ownerSM.getExecutionContext().getTransaction().isActive())
        {
            ownerSM.getExecutionContext().processNontransactionalUpdate();
        }
        return backingStore != null ? backingSuccess : delegateSuccess;
    }

    /**
     * Method to add a Collection to the LinkedList.
     * @param elements The collection
     * @return Whether it was added ok.
     **/
    public boolean addAll(Collection elements)
    {
        if (useCache)
        {
            loadFromStore();
        }

        boolean backingSuccess = true;
        if (backingStore != null)
        {
            if (SCOUtils.useQueuedUpdate(ownerSM))
            {
                for (Object element : elements)
                {
                    ownerSM.getExecutionContext().addOperationToQueue(new CollectionAddOperation(ownerSM, backingStore, element));
                }
            }
            else
            {
                try
                {
                    backingSuccess = backingStore.addAll(ownerSM, elements, useCache ? delegate.size() : -1);
                }
                catch (NucleusDataStoreException dse)
                {
                    throw new IllegalArgumentException(Localiser.msg("023013", "add", ownerMmd.getName(), dse), dse);
                }
            }
        }

        // Only make it dirty after adding the element(s) to the datastore so we give it time
        // to be inserted - otherwise jdoPreStore on this object would have been called before completing the addition
        makeDirty();

        boolean delegateSuccess = delegate.addAll(elements);

        if (ownerSM != null && !ownerSM.getExecutionContext().getTransaction().isActive())
        {
            ownerSM.getExecutionContext().processNontransactionalUpdate();
        }
        return backingStore != null ? backingSuccess : delegateSuccess;
    }

    /**
     * Method to add a Collection to a position in the LinkedList.
     * @param index Position to insert the collection.
     * @param elements The collection
     * @return Whether it was added ok.
     **/
    public boolean addAll(int index, Collection elements)
    {
        if (useCache)
        {
            loadFromStore();
        }

        boolean backingSuccess = true;
        if (backingStore != null)
        {
            if (SCOUtils.useQueuedUpdate(ownerSM))
            {
                int pos = index;
                for (Object element : elements)
                {
                    ownerSM.getExecutionContext().addOperationToQueue(new ListAddAtOperation(ownerSM, backingStore, pos++, element));
                }
            }
            else
            {
                try
                {
                    backingSuccess = backingStore.addAll(ownerSM, elements, index, useCache ? delegate.size() : -1);
                }
                catch (NucleusDataStoreException dse)
                {
                    throw new IllegalArgumentException(Localiser.msg("023013", "addAll", ownerMmd.getName(), dse), dse);
                }
            }
        }

        // Only make it dirty after adding the element(s) to the datastore so we give it time
        // to be inserted - otherwise jdoPreStore on this object would have been called before completing the addition
        makeDirty();

        boolean delegateSuccess = delegate.addAll(index, elements);

        if (ownerSM != null && !ownerSM.getExecutionContext().getTransaction().isActive())
        {
            ownerSM.getExecutionContext().processNontransactionalUpdate();
        }
        return backingStore != null ? backingSuccess : delegateSuccess;
    }

    /**
     * Method to add an element as first in the LinkedList.
     * @param element The new element
     **/
    public void addFirst(E element)
    {
        add(0, element);
    }

    /**
     * Method to add an element as last in the LinkedList.
     * @param element The new element
     **/
    public void addLast(E element)
    {
        add(size(), element);
    }

    /**
     * Method to clear the LinkedList.
     */
    public void clear()
    {
        makeDirty();
        delegate.clear();

        if (backingStore != null)
        {
            if (SCOUtils.useQueuedUpdate(ownerSM))
            {
                ownerSM.getExecutionContext().addOperationToQueue(new CollectionClearOperation(ownerSM, backingStore));
            }
            else
            {
                backingStore.clear(ownerSM);
            }
        }

        if (ownerSM != null && !ownerSM.getExecutionContext().getTransaction().isActive())
        {
            ownerSM.getExecutionContext().processNontransactionalUpdate();
        }
    }

    /**
     * Method to remove an element from the LinkedList.
     * @param index The element position.
     * @return The object that was removed
     **/
    public E remove(int index)
    {
        makeDirty();
 
        if (useCache)
        {
            loadFromStore();
        }

        int size = useCache ? delegate.size() : -1;
        E delegateObject = useCache ? delegate.remove(index) : null;

        E backingObject = null;
        if (backingStore != null)
        {
            if (SCOUtils.useQueuedUpdate(ownerSM))
            {
                backingObject = delegateObject;
                ownerSM.getExecutionContext().addOperationToQueue(new ListRemoveAtOperation(ownerSM, backingStore, index));
            }
            else
            {
                try
                {
                    backingObject = backingStore.remove(ownerSM, index, size);
                }
                catch (NucleusDataStoreException dse)
                {
                    NucleusLogger.PERSISTENCE.warn(Localiser.msg("023013", "remove", ownerMmd.getName(), dse));
                    backingObject = null;
                }
            }
        }

        if (ownerSM != null && !ownerSM.getExecutionContext().getTransaction().isActive())
        {
            ownerSM.getExecutionContext().processNontransactionalUpdate();
        }

        return backingStore != null ? backingObject : delegateObject;
    }

    /**
     * Method to remove an element from the LinkedList.
     * @param element The element
     * @return Whether the object was removed ok
     */
    public boolean remove(Object element)
    {
        return remove(element, true);
    }

    /**
     * Method to remove an element from the collection, and observe the flag for whether to allow cascade delete.
     * @param element The element
     * @param allowCascadeDelete Whether to allow cascade delete
     */
    public boolean remove(Object element, boolean allowCascadeDelete)
    {
        makeDirty();

        if (useCache)
        {
            loadFromStore();
        }

        int size = useCache ? delegate.size() : -1;
        boolean contained = delegate.contains(element);
        boolean delegateSuccess = delegate.remove(element);

        boolean backingSuccess = true;
        if (backingStore != null)
        {
            if (SCOUtils.useQueuedUpdate(ownerSM))
            {
                backingSuccess = contained;
                if (backingSuccess)
                {
                    ownerSM.getExecutionContext().addOperationToQueue(new CollectionRemoveOperation(ownerSM, backingStore, element, allowCascadeDelete));
                }
            }
            else
            {
                try
                {
                    backingSuccess = backingStore.remove(ownerSM, element, size, allowCascadeDelete);
                }
                catch (NucleusDataStoreException dse)
                {
                    NucleusLogger.PERSISTENCE.warn(Localiser.msg("023013", "remove", ownerMmd.getName(), dse));
                    backingSuccess = false;
                }
            }
        }

        if (ownerSM != null && !ownerSM.getExecutionContext().getTransaction().isActive())
        {
            ownerSM.getExecutionContext().processNontransactionalUpdate();
        }

        return backingStore != null ? backingSuccess : delegateSuccess;
    }

    /**
     * Method to remove a Collection from the LinkedList.
     * @param elements The collection
     * @return Whether it was removed ok.
     **/
    public boolean removeAll(Collection elements)
    {
        if (elements == null)
        {
            throw new NullPointerException();
        }
        else if (elements.isEmpty())
        {
            return true;
        }

        makeDirty();

        if (useCache)
        {
            loadFromStore();
        }

        int size = useCache ? delegate.size() : -1;
        Collection contained = null;
        if (backingStore != null && SCOUtils.useQueuedUpdate(ownerSM))
        {
            // Check which are contained before updating the delegate
            contained = new java.util.HashSet();
            for (Object elem : elements)
            {
                if (contains(elem))
                {
                    contained.add(elem);
                }
            }
        }
        boolean delegateSuccess = delegate.removeAll(elements);

        if (backingStore != null && ownerSM != null)
        {
            boolean backingSuccess = true;
            if (SCOUtils.useQueuedUpdate(ownerSM))
            {
                if (contained != null && !contained.isEmpty())
                {
                    backingSuccess = false;
                    for (Object element : contained)
                    {
                        backingSuccess = true;
                        ownerSM.getExecutionContext().addOperationToQueue(new CollectionRemoveOperation(ownerSM, backingStore, element, true));
                    }
                }
            }
            else
            {
                try
                {
                    backingSuccess = backingStore.removeAll(ownerSM, elements, size);
                }
                catch (NucleusDataStoreException dse)
                {
                    NucleusLogger.PERSISTENCE.warn(Localiser.msg("023013", "removeAll", ownerMmd.getName(), dse));
                    backingSuccess = false;
                }
            }

            if (!ownerSM.getExecutionContext().getTransaction().isActive())
            {
                ownerSM.getExecutionContext().processNontransactionalUpdate();
            }

            return backingSuccess;
        }

        if (ownerSM != null && !ownerSM.getExecutionContext().getTransaction().isActive())
        {
            ownerSM.getExecutionContext().processNontransactionalUpdate();
        }
        return delegateSuccess;
    }

    /**
     * Method to remove the first element from the LinkedList.
     * @return The object that was removed
     **/
    public E removeFirst()
    {
        return remove(0);
    }

    /**
     * Method to remove the last element from the LinkedList.
     * @return The object that was removed
     **/
    public E removeLast()
    {
        return remove(size()-1);
    }

    /**
     * Method to retain a Collection of elements (and remove all others).
     * @param c The collection to retain
     * @return Whether they were retained successfully.
     */
    public boolean retainAll(java.util.Collection c)
    {
        makeDirty();

        if (useCache)
        {
            loadFromStore();
        }

        boolean modified = false;
        Iterator iter=iterator();
        while (iter.hasNext())
        {
            Object element = iter.next();
            if (!c.contains(element))
            {
                iter.remove();
                modified = true;
            }
        }

        if (ownerSM != null && !ownerSM.getExecutionContext().getTransaction().isActive())
        {
            ownerSM.getExecutionContext().processNontransactionalUpdate();
        }
        return modified;
    }

    /**
     * Wrapper addition that allows turning off of the dependent-field checks
     * when doing the position setting. This means that we can prevent the deletion of
     * the object that was previously in that position. This particular feature is used
     * when attaching a list field and where some elements have changed positions.
     * @param index The position
     * @param element The new element
     * @return The element previously at that position
     */
    public E set(int index, E element, boolean allowDependentField)
    {
        // Reject inappropriate elements
        if (!allowNulls && element == null)
        {
            throw new NullPointerException("Nulls not allowed for collection at field " + ownerMmd.getName() + " but element is null");
        }

        makeDirty();

        if (useCache)
        {
            loadFromStore();
        }

        E delegateReturn = delegate.set(index, element);
        if (backingStore != null)
        {
            if (SCOUtils.useQueuedUpdate(ownerSM))
            {
                ownerSM.getExecutionContext().addOperationToQueue(new ListSetOperation(ownerSM, backingStore, index, element, allowDependentField));
            }
            else
            {
                backingStore.set(ownerSM, index, element, allowDependentField);
            }
        }

        if (ownerSM != null && !ownerSM.getExecutionContext().getTransaction().isActive())
        {
            ownerSM.getExecutionContext().processNontransactionalUpdate();
        }
        return delegateReturn;
    }

    /**
     * Method to set the element at a position in the LinkedList.
     * @param index The position
     * @param element The new element
     * @return The element previously at that position
     **/
    public E set(int index, E element)
    {
        return set(index, element, !sorting);
    }

    /**
     * The writeReplace method is called when ObjectOutputStream is preparing
     * to write the object to the stream. The ObjectOutputStream checks whether
     * the class defines the writeReplace method. If the method is defined, the
     * writeReplace method is called to allow the object to designate its 
     * replacement in the stream. The object returned should be either of the
     * same type as the object passed in or an object that when read and
     * resolved will result in an object of a type that is compatible with
     * all references to the object.
     *
     * @return the replaced object
     * @throws ObjectStreamException if an error occurs
     */
    protected Object writeReplace() throws ObjectStreamException
    {
        if (useCache)
        {
            loadFromStore();
            return new java.util.LinkedList(delegate);
        }

        // TODO Cater for non-cached collection, load elements in a DB call.
        return new java.util.LinkedList(delegate);
    }

    @Override
    public Spliterator spliterator()
    {
        if (backingStore != null && useCache && !isCacheLoaded)
        {
            loadFromStore();
        }
        // TODO If using backing store yet not caching, then this will fail
        return delegate.spliterator();
    }

    @Override
    public Stream stream()
    {
        if (backingStore != null && useCache && !isCacheLoaded)
        {
            loadFromStore();
        }
        // TODO If using backing store yet not caching, then this will fail
        return delegate.stream();
    }

    @Override
    public Stream parallelStream()
    {
        if (backingStore != null && useCache && !isCacheLoaded)
        {
            loadFromStore();
        }
        // TODO If using backing store yet not caching, then this will fail
        return delegate.parallelStream();
    }
}
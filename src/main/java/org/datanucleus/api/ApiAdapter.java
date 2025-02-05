/**********************************************************************
Copyright (c) 2006 Erik Bengtson and others. All rights reserved.
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
package org.datanucleus.api;

import java.io.Serializable;
import java.util.Map;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.ExecutionContext;
import org.datanucleus.enhancement.Detachable;
import org.datanucleus.enhancement.Persistable;
import org.datanucleus.enhancement.StateManager;
import org.datanucleus.exceptions.NucleusException;
import org.datanucleus.metadata.AbstractClassMetaData;
import org.datanucleus.metadata.MetaDataManager;
import org.datanucleus.state.LifeCycleState;

/**
 * Adapter to allow the core runtime to expose multiple APIs to clients.
 */
public interface ApiAdapter extends Serializable
{
    /**
     * Accessor for the name of the API. JDO, JPA, Jakarta etc
     * @return Name of the API
     */
    String getName();

    /**
     * Accessor for whether this member type is default persistent.
     * @param type Member type
     * @return Whether it is by default persistent
     */
    boolean isMemberDefaultPersistent(Class type);

    String getXMLMetaDataForClass(AbstractClassMetaData cmd, String prefix, String indent);

    /**
     * Method to return any default location that doesn't need specifying for "mapping-file" in the persistence.xml.
     * With JPA/Jakarta this is META-INF/orm.xml. With JDO we don't have such a location.
     * @return The default location to try for a mapping-file for this API
     */
    String getDefaultMappingFileLocation();

    // ------------------------------ Object Lifecycle --------------------------------

    /**
     * Whether the provided object is currently managed (has an ExecutionContext).
     * @param pc The persistable object
     * @return Whether it is managed
     */
    default boolean isManaged(Object pc)
    {
        return (getExecutionContext(pc) != null);
    }

    /**
     * Accessor for the state manager for the object.
     * @param pc The object
     * @return The StateManager managing this object
     */
    default StateManager getStateManager(Object pc)
    {
        if (pc == null)
        {
            return null;
        }

        if (pc instanceof Persistable)
        {
            return ((Persistable)pc).dnGetStateManager();
        }
        return null;
    }

    /**
     * Method to return the ExecutionContext (if any) associated with the passed object.
     * Supports persistable objects, and PersistenceManager.
     * @param pc The object
     * @return The ExecutionContext
     */
    ExecutionContext getExecutionContext(Object pc);

    /**
     * Returns the LifeCycleState for the state constant.
     * @param stateType the type as integer
     * @return the type as LifeCycleState object
     */
    LifeCycleState getLifeCycleState(int stateType);

    /**
     * Accessor for whether the passed object is persistent.
     * @param obj The object
     * @return Whether it is persistent
     */
    default boolean isPersistent(Object obj)
    {
        return obj instanceof Persistable ? ((Persistable)obj).dnIsPersistent() : false;
    }

    /**
     * Accessor for whether the passed object is new.
     * @param obj The object
     * @return Whether it is new
     */
    default boolean isNew(Object obj)
    {
        return obj instanceof Persistable ? ((Persistable)obj).dnIsNew() : false;
    }

    /**
     * Accessor for whether the passed object is dirty.
     * @param obj The object
     * @return Whether it is dirty
     */
    default boolean isDirty(Object obj)
    {
        return obj instanceof Persistable ? ((Persistable)obj).dnIsDirty() : false;
    }

    /**
     * Accessor for whether the passed object is deleted.
     * @param obj The object
     * @return Whether it is deleted
     */
    default boolean isDeleted(Object obj)
    {
        return obj instanceof Persistable ? ((Persistable)obj).dnIsDeleted() : false;
    }

    /**
     * Accessor for whether the passed object is detached.
     * @param obj The object
     * @return Whether it is detached
     */
    default boolean isDetached(Object obj)
    {
        return obj instanceof Persistable ? ((Persistable)obj).dnIsDetached() : false;
    }

    /**
     * Accessor for whether the passed object is transactional.
     * @param obj The object
     * @return Whether it is transactional
     */
    default boolean isTransactional(Object obj)
    {
        return obj instanceof Persistable ? ((Persistable)obj).dnIsTransactional() : false;
    }

    /**
     * Method to return if the passed object is persistable using this API.
     * @param obj The object
     * @return Whether it is persistable
     */
    default boolean isPersistable(Object obj)
    {
        return obj == null ? false : obj instanceof Persistable;
    }

    /**
     * Utility method to check if the specified class is of a type that can be persisted for this API.
     * @param cls The class to check
     * @return Whether the class is persistable using this API.
     */
    default boolean isPersistable(Class cls)
    {
        return cls == null ? false : Persistable.class.isAssignableFrom(cls);
    }

    /**
     * Method to return if the passed object is detachable using this API.
     * @param obj The object
     * @return Whether it is detachable
     */
    default boolean isDetachable(Object obj)
    {
        return obj == null ? false : obj instanceof Detachable;
    }

    /**
     * Accessor for the object state.
     * @param pc Persistable object
     * @return The state ("persistent-clean", "detached-dirty" etc)
     */
    default String getObjectState(Object pc)
    {
        if (pc == null)
        {
            return null;
        }

        if (isDetached(pc))
        {
            if (isDirty(pc))
            {
                // Detached Dirty
                return "detached-dirty";
            }
            // Detached Not Dirty
            return "detached-clean";
        }

        if (isPersistent(pc))
        {
            if (isTransactional(pc))
            {
                if (isDirty(pc))
                {
                    if (isNew(pc))
                    {
                        if (isDeleted(pc))
                        {
                            // Persistent Transactional Dirty New Deleted
                            return "persistent-new-deleted";
                        }
                        // Persistent Transactional Dirty New Not Deleted
                        return "persistent-new";
                    }

                    if (isDeleted(pc))
                    {
                        // Persistent Transactional Dirty Not New Deleted
                        return "persistent-deleted";
                    }
                    // Persistent Transactional Dirty Not New Not Deleted
                    return "persistent-dirty";
                }

                // Persistent Transactional Not Dirty
                return "persistent-clean";
            }

            if (isDirty(pc))
            {
                // Persistent Nontransactional Dirty
                return "persistent-nontransactional-dirty";
            }
            // Persistent Nontransactional Not Dirty
            return "hollow/persistent-nontransactional";
        }

        if (isTransactional(pc))
        {
            if (isDirty(pc))
            {
                // Not Persistent Transactional Dirty
                return "transient-dirty";
            }

            // Not Persistent Transactional Not Dirty
            return "transient-clean";
        }

        // Not Persistent Not Transactional
        return "transient";
    }

    /**
     * Method to make the member of the persistable object dirty.
     * @param obj The object
     * @param member Name of the member
     */
    default void makeDirty(Object obj, String member)
    {
        ((Persistable)obj).dnMakeDirty(member);
    }

    // ------------------------------ Object Identity  --------------------------------

    /**
     * Method to return the object identity for the passed persistable object.
     * Returns null if it is not persistable, or has no identity.
     * @param obj The object
     * @return The identity
     */
    default Object getIdForObject(Object obj)
    {
        return obj instanceof Persistable ? ((Persistable)obj).dnGetObjectId() : null;
    }

    /**
     * Method to return the object version for the passed persistable object.
     * Returns null if it is not persistable, or not versioned.
     * @param obj The object
     * @return The version
     */
    default Object getVersionForObject(Object obj)
    {
        return obj instanceof Persistable ? ((Persistable)obj).dnGetVersion() : null;
    }

    /**
     * Utility to check if a primary-key class is valid.
     * Will throw a InvalidPrimaryKeyException if it is invalid, otherwise returning true.
     * @param pkClass The Primary Key class
     * @param cmd AbstractClassMetaData for the persistable class
     * @param clr the ClassLoaderResolver
     * @param noOfPkFields Number of primary key fields
     * @param mmgr MetaData manager
     * @return Whether it is valid
     */
    boolean isValidPrimaryKeyClass(Class pkClass, AbstractClassMetaData cmd, ClassLoaderResolver clr, int noOfPkFields, MetaDataManager mmgr);

    /**
     * Method to set the pk fields of the object from the provided identity.
     * @param pc The persistable object
     * @param fm ObjectIdFieldConsumer
     * @param id The identity
     */
    default void copyKeyFieldsFromIdToObject(Object pc, Persistable.ObjectIdFieldConsumer fm, Object id)
    {
        ((Persistable)pc).dnCopyKeyFieldsFromObjectId(fm, id);
    }

    // ------------------------------ Persistence --------------------------------

    /**
     * Whether the API allows (re-)persistence of a deleted object.
     * @return Whether you can call persist on a deleted object
     */
    boolean allowPersistOfDeletedObject();

    /**
     * Whether the API allows deletion of a non-persistent object.
     * @return Whether you can call delete on an object not yet persisted
     */
    boolean allowDeleteOfNonPersistentObject();

    /**
     * Whether the API allows reading a field of a deleted object.
     * @return Whether you can read after deleting
     */
    boolean allowReadFieldOfDeletedObject();

    /**
     * Whether the API requires clearing of the fields of an object when it is deleted.
     * @return Whether to clear loaded fields at delete
     */
    boolean clearLoadedFlagsOnDeleteObject();

    /**
     * Method to return the default setting for cascading persist of a field
     * @return default persist cascade setting.
     */
    boolean getDefaultCascadePersistForField();

    /**
     * Method to return the default setting for cascading delete of a field
     * @return default cascade delete setting.
     */
    boolean getDefaultCascadeDeleteForField();

    /**
     * Method to return the default setting for cascading attach of a field
     * @return default cascade attach setting.
     */
    boolean getDefaultCascadeAttachForField();

    /**
     * Method to return the default setting for cascading detach of a field
     * @return default cascade detach setting.
     */
    boolean getDefaultCascadeDetachForField();

    /**
     * Method to return the default setting for cascading refresh of a field
     * @return default cascade refresh setting.
     */
    boolean getDefaultCascadeRefreshForField();

    /**
     * Method to return the default DFG setting for a persistable field.
     * @return DFG default for persistable field
     */
    boolean getDefaultDFGForPersistableField();

    /**
     * Method to return a set of default properties for the factory (PMF, EMF, etc)
     * @return The default properties (if any) for this API
     */
    Map getDefaultFactoryProperties();

    /**
     * Whether we should add in metadata when using persistent properties and a property is not specified.
     * True means we add in metadata when not explicitly specified. False means if a property is not defined then it is not persistent.
     * @return Whether to default it (JDO = false, JPA/Jakarta = true)
     */
    boolean getDefaultPersistentPropertyWhenNotSpecified();

    // ------------------------------ StateManager --------------------------------

    /**
     * Convenience method to convert the passed NucleusException into an exception for the API.
     * @param ne The NucleusException
     * @return The Api Exception
     */
    RuntimeException getApiExceptionForNucleusException(NucleusException ne);

    /**
     * Convenience method to return a user exception appropriate for this API when an unexpected
     * exception occurs.
     * @param msg The message
     * @param e The cause (if any)
     * @return The API exception
     */
    RuntimeException getUserExceptionForException(String msg, Exception e);

    /**
     * Convenience method to return a datastore exception appropriate for this API.
     * @param msg The message
     * @param e The cause (if any)
     * @return The exception
     */
    RuntimeException getDataStoreExceptionForException(String msg, Exception e);
}
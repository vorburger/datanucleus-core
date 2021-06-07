/**********************************************************************
Copyright (c) 2004 Brendan de Beer and others. All rights reserved.
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
2004 Brendan de Beer - Initial contributor for conversion methods
2005 Erik Bengtson - refactor mapping
2005 Andy Jefferson - added Timestamp/String converters
    ...
**********************************************************************/
package org.datanucleus.util;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Currency;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.metadata.ColumnMetaData;
import org.datanucleus.metadata.FieldRole;
import org.datanucleus.metadata.JdbcType;
import org.datanucleus.metadata.MetaData;
import org.datanucleus.metadata.MetaDataUtils;

/**
 * Class with methods for type conversion.
 */
public class TypeConversionHelper
{
    /**
     * Convert the value to a instance of the given type. The value is converted only if the type can't be assigned from the current type of the value instance. 
     * @param value the value to be converted
     * @param type the type of the expected object returned from the conversion
     * @return the converted object, or the input value if the object can't be converted
     */
    public static Object convertTo(Object value, Class type)
    {
        if (type == null || value == null)
        {
            return value;
        }
        if (type.isPrimitive())
        {
            // We are returning an object-based value, so convert requested primitive as the object wrapper equivalent
            type = ClassUtils.getWrapperTypeForPrimitiveType(type);
        }

        if (type.isAssignableFrom(value.getClass()))
        {
            // Already in the correct type
            return value;
        }

        if (type == Short.class)
        {
            if (value instanceof Number)
            {
                return Short.valueOf(((Number)value).shortValue());
            }
            return Short.valueOf(value.toString());
        }
        else if (type == Character.class)
        {
            return Character.valueOf(value.toString().charAt(0));
        }
        else if (type == Integer.class)
        {
            if (value instanceof Number)
            {
                return Integer.valueOf(((Number)value).intValue());
            }
            return Integer.valueOf(value.toString());
        }
        else if (type == Long.class)
        {
            if (value instanceof Number)
            {
                return Long.valueOf(((Number)value).longValue());
            }
            return Long.valueOf(value.toString());
        }
        else if (type == Boolean.class)
        {
            if (value instanceof Long)
            {
                // Convert a Long (0, 1) to Boolean (FALSE, TRUE) and null otherwise
                return (Long)value == 0 ? Boolean.FALSE : ((Long)value == 1 ? Boolean.TRUE : null);
            }
            else if (value instanceof Integer)
            {
                // Convert a Integer (0, 1) to Boolean (FALSE, TRUE) and null otherwise
                return (Integer)value == 0 ? Boolean.FALSE : ((Integer)value == 1 ? Boolean.TRUE : null);
            }
            else if (value instanceof Short)
            {
                // Convert a Short (0, 1) to Boolean (FALSE, TRUE) and null otherwise
                return (Short)value == 0 ? Boolean.FALSE : ((Short)value == 1 ? Boolean.TRUE : null);
            }
            return Boolean.valueOf(value.toString());
        }
        else if (type == Byte.class)
        {
            return Byte.valueOf(value.toString());
        }
        else if (type == Float.class)
        {
            if (value instanceof Number)
            {
                return Float.valueOf(((Number)value).floatValue());
            }
            return Float.valueOf(value.toString());
        }
        else if (type == Double.class)
        {
            if (value instanceof Number)
            {
                return Double.valueOf(((Number)value).doubleValue());
            }
            return Double.valueOf(value.toString());
        }
        else if (type == BigDecimal.class)
        {
            return new BigDecimal(value.toString());
        }
        else if (type == BigInteger.class)
        {
            return new BigInteger(value.toString());
        }
        else if (type == String.class)
        {
            return value.toString();
        }
        else if (type == Date.class)
        {
            if (value instanceof java.time.LocalDate)
            {
                java.time.LocalDate localDate = (java.time.LocalDate)value;
                return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            }
            else if (value instanceof java.sql.Date)
            {
                return new java.sql.Date(((Date)value).getTime());
            }
        }
        else if (type == java.sql.Timestamp.class)
        {
            if (value instanceof Date)
            {
                return new java.sql.Timestamp(((Date)value).getTime());
            }
            return java.sql.Timestamp.valueOf(value.toString());
        }
        else if (type == java.sql.Date.class)
        {
            if (value instanceof Date)
            {
                return new java.sql.Date(((Date)value).getTime());
            }
            else if (value instanceof java.time.LocalDate)
            {
                java.time.LocalDate localDate = (java.time.LocalDate)value;
                return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            }
            return java.sql.Date.valueOf(value.toString());
        }
        else if (type == java.sql.Time.class)
        {
            if (value instanceof Date)
            {
                return new java.sql.Time(((Date)value).getTime());
            }
            return java.sql.Time.valueOf(value.toString());
        }
        else if (type == java.time.LocalDate.class)
        {
            if (value instanceof java.sql.Timestamp)
            {
                return ((java.sql.Timestamp)value).toLocalDateTime().toLocalDate();
            }
            else if (value instanceof java.sql.Date)
            {
                return ((java.sql.Date)value).toLocalDate();
            }
            else if (value instanceof Date)
            {
                Date date = (Date)value;
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault()).toLocalDate();
            }
        }
        else if (type == java.time.LocalDateTime.class)
        {
            if (value instanceof java.sql.Timestamp)
            {
                return ((java.sql.Timestamp)value).toLocalDateTime();
            }
            else if (value instanceof Date)
            {
                Date date = (Date)value;
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault()).toLocalDate();
            }
        }
        else if (type == java.time.LocalTime.class)
        {
            if (value instanceof java.sql.Timestamp)
            {
                return ((java.sql.Timestamp)value).toLocalDateTime().toLocalTime();
            }
            else if (value instanceof java.sql.Time)
            {
                return ((java.sql.Time)value).toLocalTime();
            }
            else if (value instanceof Date)
            {
                Date date = (Date)value;
                return LocalTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault());
            }
        }
        else if (type == UUID.class)
        {
            if (value instanceof String)
            {
                return UUID.fromString((String)value);
            }
        }
        else if (type == TimeZone.class)
        {
            if (value instanceof String)
            {
                return TimeZone.getTimeZone((String)value);
            }
        }
        else if (type == Currency.class)
        {
            if (value instanceof String)
            {
                return Currency.getInstance((String)value);
            }
        }
        else if (type == Locale.class)
        {
            if (value instanceof String)
            {
                return I18nUtils.getLocaleFromString((String)value);
            }
        }

        NucleusLogger.PERSISTENCE.warn("Request to convert value of type " + value.getClass().getName() + " to type " + type.getName() + " but this is not yet supported. " +
                "Raise an issue and contribute the code to support this conversion, with a testcase that demonstrates the problem");
        return value;
    }

    /**
     * Converts a string in JDBC timestamp escape format to a Timestamp object.
     * To be precise, we prefer to find a JDBC escape type sequence in the format "yyyy-mm-dd hh:mm:ss.fffffffff", but this does not accept
     * other separators of fields, so as long as the numbers are in the order year, month, day, hour, minute, second then we accept it.
     * @param s Timestamp string
     * @param cal The Calendar to use for conversion
     * @return Corresponding <i>java.sql.Timestamp</i> value.
     * @exception java.lang.IllegalArgumentException Thrown if the format of the
     * String is invalid
     */
    public static Timestamp stringToTimestamp(String s, Calendar cal)
    {
        int[] numbers = convertStringToIntArray(s);
        if (numbers == null || numbers.length < 6)
        {
            throw new IllegalArgumentException(Localiser.msg("030003", s));
        }

        int year = numbers[0];
        int month = numbers[1];
        int day = numbers[2];
        int hour = numbers[3];
        int minute = numbers[4];
        int second = numbers[5];
        int nanos = 0;
        if (numbers.length > 6)
        {
            StringBuilder zeroedNanos = new StringBuilder("" + numbers[6]);
            if (zeroedNanos.length() < 9)
            {
                // Add trailing zeros
                int numZerosToAdd = 9-zeroedNanos.length();
                for (int i=0;i<numZerosToAdd;i++)
                {
                    zeroedNanos.append("0");
                }
                nanos = Integer.valueOf(zeroedNanos.toString());
            }
            else
            {
                nanos = numbers[6];
            }
        }

        Calendar thecal = cal;
        if (cal == null)
        {
            thecal = new GregorianCalendar();
        }
        thecal.set(Calendar.ERA, GregorianCalendar.AD);
        thecal.set(Calendar.YEAR, year);
        thecal.set(Calendar.MONTH, month - 1);
        thecal.set(Calendar.DATE, day);
        thecal.set(Calendar.HOUR_OF_DAY, hour);
        thecal.set(Calendar.MINUTE, minute);
        thecal.set(Calendar.SECOND, second);
        Timestamp ts = new Timestamp(thecal.getTime().getTime());
        ts.setNanos(nanos);

        return ts;
    }

    /**
     * Convenience method to convert a String containing numbers (separated by assorted
     * characters) into an int array. The separators can be ' '  '-'  ':'  '.'  ',' etc.
     * @param str The String
     * @return The int array
     */
    private static int[] convertStringToIntArray(String str)
    {
        if (str == null)
        {
            return null;
        }

        int[] values = null;
        ArrayList list = new ArrayList();

        int start = -1;
        for (int i=0;i<str.length();i++)
        {
            if (start == -1 && Character.isDigit(str.charAt(i)))
            {
                start = i;
            }
            if (start != i && start >= 0)
            {
                if (!Character.isDigit(str.charAt(i)))
                {
                    list.add(Integer.valueOf(str.substring(start, i)));
                    start = -1;
                }
            }
            if (i == str.length()-1 && start >= 0)
            {
                list.add(Integer.valueOf(str.substring(start)));
            }
        }

        if (!list.isEmpty())
        {
            values = new int[list.size()];
            Iterator iter = list.iterator();
            int n = 0;
            while (iter.hasNext())
            {
                values[n++] = ((Integer)iter.next()).intValue();
            }
        }
        return values;
    }

    public static JdbcType getJdbcTypeForEnum(AbstractMemberMetaData mmd, FieldRole role, ClassLoaderResolver clr)
    {
        JdbcType jdbcType = JdbcType.VARCHAR;
        if (mmd != null)
        {
            String methodName = null;
            Class enumType = null;
            ColumnMetaData[] colmds = null;
            if (role == FieldRole.ROLE_FIELD)
            {
                enumType = mmd.getType();
                if (mmd.hasExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER))
                {
                    methodName = mmd.getValueForExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER);
                }
                colmds = mmd.getColumnMetaData();
            }
            else if (role == FieldRole.ROLE_COLLECTION_ELEMENT || role == FieldRole.ROLE_ARRAY_ELEMENT)
            {
                if (mmd.getElementMetaData() != null)
                {
                    enumType = clr.classForName(mmd.hasCollection() ? mmd.getCollection().getElementType() : mmd.getArray().getElementType());
                    if (mmd.getElementMetaData().hasExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER))
                    {
                        methodName = mmd.getElementMetaData().getValueForExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER);
                    }
                    colmds = mmd.getElementMetaData().getColumnMetaData();
                }
            }
            else if (role == FieldRole.ROLE_MAP_KEY)
            {
                if (mmd.getKeyMetaData() != null)
                {
                    enumType = clr.classForName(mmd.getMap().getKeyType());
                    if (mmd.getKeyMetaData().hasExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER))
                    {
                        methodName = mmd.getKeyMetaData().getValueForExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER);
                    }
                    colmds = mmd.getKeyMetaData().getColumnMetaData();
                }
            }
            else if (role == FieldRole.ROLE_MAP_VALUE)
            {
                if (mmd.getValueMetaData() != null)
                {
                    enumType = clr.classForName(mmd.getMap().getValueType());
                    if (mmd.getValueMetaData().hasExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER))
                    {
                        methodName = mmd.getValueMetaData().getValueForExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER);
                    }
                    colmds = mmd.getValueMetaData().getColumnMetaData();
                }
            }

            if (methodName == null)
            {
                if (colmds != null && colmds.length == 1 && colmds[0].getJdbcType() != null)
                {
                    jdbcType = colmds[0].getJdbcType();
                }
            }
            else
            {
                try
                {
                    Method getterMethod = ClassUtils.getMethodForClass(enumType, methodName, null);
                    Class returnType = getterMethod.getReturnType();
                    if (returnType == short.class || returnType == int.class || returnType == long.class || Number.class.isAssignableFrom(returnType))
                    {
                        return JdbcType.INTEGER;
                    }
                    return JdbcType.VARCHAR;
                }
                catch (Exception e)
                {
                    NucleusLogger.PERSISTENCE.warn("Specified enum value-getter for method " + methodName + " on field " + mmd.getFullFieldName() + " gave an error on extracting the value", e);
                }
            }
        }

        return jdbcType;
    }

    public static Object getEnumForStoredValue(AbstractMemberMetaData mmd, FieldRole role, Object value, ClassLoaderResolver clr)
    {
        Class enumType = mmd.getType();
        String valueGetterMethodName = null;
        String getEnumStatisMethodName = null;
        if (role == FieldRole.ROLE_FIELD && mmd.hasExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER))
        {
            valueGetterMethodName = mmd.getValueForExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER);
            getEnumStatisMethodName = mmd.getValueForExtension(MetaData.EXTENSION_MEMBER_ENUM_GETTER_BY_VALUE);
        }
        else if (role == FieldRole.ROLE_COLLECTION_ELEMENT)
        {
            enumType = clr.classForName(mmd.getCollection().getElementType());
            if (mmd.getElementMetaData() != null && mmd.getElementMetaData().hasExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER))
            {
                valueGetterMethodName = mmd.getElementMetaData().getValueForExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER);
                getEnumStatisMethodName = mmd.getElementMetaData().getValueForExtension(MetaData.EXTENSION_MEMBER_ENUM_GETTER_BY_VALUE);
            }
        }
        else if (role == FieldRole.ROLE_ARRAY_ELEMENT)
        {
            enumType = clr.classForName(mmd.getArray().getElementType());
            if (mmd.getElementMetaData() != null && mmd.getElementMetaData().hasExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER))
            {
                valueGetterMethodName = mmd.getElementMetaData().getValueForExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER);
                getEnumStatisMethodName = mmd.getElementMetaData().getValueForExtension(MetaData.EXTENSION_MEMBER_ENUM_GETTER_BY_VALUE);
            }
        }
        else if (role == FieldRole.ROLE_MAP_KEY)
        {
            enumType = clr.classForName(mmd.getMap().getKeyType());
            if (mmd.getKeyMetaData() != null && mmd.getKeyMetaData().hasExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER))
            {
                valueGetterMethodName = mmd.getKeyMetaData().getValueForExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER);
                getEnumStatisMethodName = mmd.getKeyMetaData().getValueForExtension(MetaData.EXTENSION_MEMBER_ENUM_GETTER_BY_VALUE);
            }
        }
        else if (role == FieldRole.ROLE_MAP_VALUE)
        {
            enumType = clr.classForName(mmd.getMap().getValueType());
            if (mmd.getValueMetaData() != null && mmd.getValueMetaData().hasExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER))
            {
                valueGetterMethodName = mmd.getValueMetaData().getValueForExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER);
                getEnumStatisMethodName = mmd.getValueMetaData().getValueForExtension(MetaData.EXTENSION_MEMBER_ENUM_GETTER_BY_VALUE);
            }
        }

        if (valueGetterMethodName != null)
        {
            // Try using the enumConstants and the valueGetter
            Object[] enumConstants = enumType.getEnumConstants();
            Method valueGetterMethod = ClassUtils.getMethodForClass(enumType, valueGetterMethodName, null);
            if (valueGetterMethod != null)
            {
                // Search for this stored value from the enum constants
                for (int i=0;i<enumConstants.length;i++)
                {
                    try
                    {
                        Object enumValue = valueGetterMethod.invoke(enumConstants[i]);
                        if (enumValue.getClass() == value.getClass())
                        {
                            if (enumValue.equals(value))
                            {
                                return enumConstants[i];
                            }
                        }
                        else if (enumValue instanceof Number && value instanceof Number)
                        {
                            // Allow for comparisons between Long/Short/Integer
                            if (((Number)enumValue).intValue() == ((Number)value).intValue())
                            {
                                return enumConstants[i];
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        // Exception in invocation. Do something
                    }
                }
            }
            else if (getEnumStatisMethodName != null)
            {
                // Deprecated : legacy method : provided static getter method to return the Enum
                try
                {
                    Method getterMethod = ClassUtils.getMethodForClass(enumType, getEnumStatisMethodName, new Class[] {int.class});
                    if (getterMethod != null)
                    {
                        return getterMethod.invoke(null, new Object[] {((Number)value).intValue()});
                    }

                    getterMethod = ClassUtils.getMethodForClass(enumType, getEnumStatisMethodName, new Class[] {short.class});
                    if (getterMethod != null)
                    {
                        return getterMethod.invoke(null, new Object[] {((Number)value).shortValue()});
                    }

                    getterMethod = ClassUtils.getMethodForClass(enumType, getEnumStatisMethodName, new Class[] {String.class});
                    if (getterMethod != null)
                    {
                        return getterMethod.invoke(null, new Object[] {(String)value});
                    }
                }
                catch (Exception e)
                {
                    NucleusLogger.PERSISTENCE.warn("Specified enum getter-by-value for field " + mmd.getFullFieldName() +
                        " gave an error on extracting the enum so just using the ordinal : " + e.getMessage());
                }
            }
        }

        return value instanceof String ? Enum.valueOf(enumType, (String)value) : enumType.getEnumConstants()[(int)value];
    }

    /**
     * Convenience method to return the "value" of an Enum, for a field and role.
     * Firstly checks for a defined method on the Enum that returns the "value", otherwise falls back to use the ordinal.
     * @param mmd Metadata for the member
     * @param role Role of the Enum in this member
     * @param myEnum The enum
     * @return The "value" (String or Integer)
     */
    public static Object getStoredValueFromEnum(AbstractMemberMetaData mmd, FieldRole role, Enum myEnum)
    {
        String methodName = null;

        boolean numeric = false; // When nothing is specified we align to the JDO default (since JPA will always have jdbcType)
        if (mmd != null)
        {
            ColumnMetaData[] colmds = null;
            if (role == FieldRole.ROLE_FIELD)
            {
                if (mmd.hasExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER))
                {
                    methodName = mmd.getValueForExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER);
                }
                colmds = mmd.getColumnMetaData();
            }
            else if (role == FieldRole.ROLE_COLLECTION_ELEMENT || role == FieldRole.ROLE_ARRAY_ELEMENT)
            {
                if (mmd.getElementMetaData() != null)
                {
                    if (mmd.getElementMetaData().hasExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER))
                    {
                        methodName = mmd.getElementMetaData().getValueForExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER);
                    }
                    colmds = mmd.getElementMetaData().getColumnMetaData();
                }
            }
            else if (role == FieldRole.ROLE_MAP_KEY)
            {
                if (mmd.getKeyMetaData() != null)
                {
                    if (mmd.getKeyMetaData().hasExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER))
                    {
                        methodName = mmd.getKeyMetaData().getValueForExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER);
                    }
                    colmds = mmd.getKeyMetaData().getColumnMetaData();
                }
            }
            else if (role == FieldRole.ROLE_MAP_VALUE)
            {
                if (mmd.getValueMetaData() != null)
                {
                    if (mmd.getValueMetaData().hasExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER))
                    {
                        methodName = mmd.getValueMetaData().getValueForExtension(MetaData.EXTENSION_MEMBER_ENUM_VALUE_GETTER);
                    }
                    colmds = mmd.getValueMetaData().getColumnMetaData();
                }
            }

            if (methodName == null)
            {
                if (colmds != null && colmds.length == 1)
                {
                    if (MetaDataUtils.isJdbcTypeNumeric(colmds[0].getJdbcType()))
                    {
                        numeric = true;
                    }
                    else if (MetaDataUtils.isJdbcTypeString(colmds[0].getJdbcType()))
                    {
                        numeric = false;
                    }
                }
            }
        }

        if (methodName != null)
        {
            try
            {
                Method getterMethod = ClassUtils.getMethodForClass(myEnum.getClass(), methodName, null);
                return getterMethod.invoke(myEnum);
            }
            catch (Exception e)
            {
                NucleusLogger.PERSISTENCE.warn("Specified enum value-getter for method " + methodName + " on field " + mmd.getFullFieldName() + " gave an error on extracting the value", e);
            }
        }

        // Fallback to standard Enum handling via ordinal() or name()
        return numeric ? myEnum.ordinal() : myEnum.name();
    }
}
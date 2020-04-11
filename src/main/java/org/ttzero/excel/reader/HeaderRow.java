/*
 * Copyright (c) 2017-2019, guanquan.wang@yandex.com All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ttzero.excel.reader;

import org.ttzero.excel.annotation.ExcelColumn;
import org.ttzero.excel.annotation.IgnoreImport;
import org.ttzero.excel.manager.Const;
import org.ttzero.excel.util.ReflectUtil;
import org.ttzero.excel.util.StringUtil;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

import static org.ttzero.excel.entity.IWorksheetWriter.isBool;
import static org.ttzero.excel.entity.IWorksheetWriter.isChar;
import static org.ttzero.excel.entity.IWorksheetWriter.isDate;
import static org.ttzero.excel.entity.IWorksheetWriter.isInt;
import static org.ttzero.excel.entity.IWorksheetWriter.isLocalDate;
import static org.ttzero.excel.entity.IWorksheetWriter.isLocalDateTime;
import static org.ttzero.excel.entity.IWorksheetWriter.isLocalTime;
import static org.ttzero.excel.util.ReflectUtil.listDeclaredFields;
import static org.ttzero.excel.util.ReflectUtil.listWriteMethods;
import static org.ttzero.excel.util.ReflectUtil.mapping;
import static org.ttzero.excel.util.StringUtil.isNotEmpty;

/**
 * @author guanquan.wang at 2019-04-17 11:55
 */
class HeaderRow extends Row {
    private String[] names;
    private Class<?> clazz;
    private Field[] fields;
    private Method[] methods;
    private int[] columns;
    private Class<?>[] fieldClazz;
    private Object t;
    /* The column name and column position mapping */
    private Map<String, Integer> mapping;

    private HeaderRow() { }

    static HeaderRow with(Row row) {
        HeaderRow hr = new HeaderRow();
        hr.names = new String[row.lc];
        hr.mapping = new HashMap<>();
        for (int i = row.fc; i < row.lc; i++) {
            hr.names[i] = row.getString(i);
            hr.mapping.put(hr.names[i], i);
        }
        // Extends from row
        hr.fc = row.fc;
        hr.lc = row.lc;
        hr.index = row.index;
        hr.cells = new Cell[hr.names.length];
        for (int i = 0; i < row.fc; i++) {
            hr.cells[i] = new Cell();
        }
        for (int i = row.fc; i < row.lc; i++) {
            Cell cell = new Cell();
            cell.setSv(hr.names[i]);
            hr.cells[i] = cell;
        }
        return hr;
    }

    final boolean is(Class<?> clazz) {
        return this.clazz != null && this.clazz == clazz;
    }

    /**
     * mapping
     *
     * @param clazz the type of binding
     * @return the header row
     */
    final HeaderRow setClass(Class<?> clazz) {
        this.clazz = clazz;
        Field[] declaredFields = listDeclaredFields(clazz);

        Method[] writeMethods = null;
        try {
            writeMethods = listWriteMethods(clazz, method -> method.getAnnotation(ExcelColumn.class) != null);
        } catch (IntrospectionException e) {
            LOGGER.warn("Get [" + clazz + "] read declared failed.", e);
        }

        Map<String, Method> tmp = new LinkedHashMap<>();

        int writeLength = methodMapping(clazz, writeMethods, tmp);
        methods = new Method[declaredFields.length + writeLength];


        int[] index = new int[declaredFields.length];
        int count = 0;
        for (int i = 0, n; i < declaredFields.length; i++) {
            Field f = declaredFields[i];
            f.setAccessible(true);
            String gs = f.getName();

            // Ignore annotation on read method
            Method method = tmp.get(gs);
            if (method != null) {
                if (method.getAnnotation(IgnoreImport.class) != null) {
                    declaredFields[i] = null;
                    continue;
                }

                method.setAccessible(true);
                methods[i] = method;
                ExcelColumn mec = method.getAnnotation(ExcelColumn.class);
                if (mec != null && isNotEmpty(mec.value())) {
                    n = check(mec.value(), gs);
                    if (n == -1) {
                        declaredFields[i] = null;
                    } else {
                        index[i] = n;
                        count++;
                    }
                    continue;
                }
            }

            // skip not import fields
            IgnoreImport nit = f.getAnnotation(IgnoreImport.class);
            if (nit != null) {
                declaredFields[i] = null;
                continue;
            }
            // field has display name
            ExcelColumn ec = f.getAnnotation(ExcelColumn.class);
            if (ec != null && isNotEmpty(ec.value())) {
                n = check(ec.value(), gs);
                if (n == -1) {
                    declaredFields[i] = null;
                    continue;
                }
            }
            // Annotation value is null
            else if (ec != null || methods[i] != null) {
                String name = f.getName();
                n = getIndex(name);
                if (n == -1 && (n = getIndex(StringUtil.toPascalCase(name))) == -1) {
                    declaredFields[i] = null;
                    continue;
                }
            } else {
                declaredFields[i] = null;
                continue;
            }

            index[i] = n;
            count++;
        }

        if (writeLength > 0) {
            System.arraycopy(writeMethods, 0, methods, declaredFields.length, writeLength);
            count += writeLength;
//            for (int i = declaredFields.length, j = 0; j < writeLength; j++) {
//                index[i++] =
//            }
        }

        this.fields = new Field[count];
        this.columns = new int[count];
        this.fieldClazz = new Class<?>[count];

        for (int i = 0, j = 0; i < declaredFields.length; i++) {
            if (declaredFields[i] != null) {
                fields[j] = declaredFields[i];
                columns[j] = index[i];
                methods[j] = methods[i];
                fieldClazz[j] = methods[i] != null ? methods[i].getParameterTypes()[0] : declaredFields[i].getType();
                j++;
            }
        }



        return this;
    }

    private int methodMapping(Class<?> clazz, Method[] writeMethods, Map<String, Method> tmp) {
        try {
            PropertyDescriptor[] propertyDescriptors = Introspector.getBeanInfo(clazz)
                .getPropertyDescriptors();
            Method[] allMethods = clazz.getMethods()
                , mergedMethods = new Method[propertyDescriptors.length];
            for (int i = 0; i < propertyDescriptors.length; i++) {
                Method method = propertyDescriptors[i].getWriteMethod();
                if (method == null) continue;
                int index = ReflectUtil.indexOf(allMethods, method);
                mergedMethods[i] = index >= 0 ? allMethods[index] : method;
            }

            return mapping(writeMethods, tmp, propertyDescriptors, mergedMethods);
        } catch (IntrospectionException e) {
            LOGGER.warn("Get " + clazz + " property descriptor failed.");
        }
        return 0;
    }

    private int check(String first, String second) {
        int n = getIndex(first);
        if (n == -1) n = getIndex(second);
        if (n == -1) {
            LOGGER.warn(clazz + " field [" + first + "] can't find in header" + Arrays.toString(names));
        }
        return n;
    }

    /**
     * mapping and instance
     *
     * @param clazz the type of binding
     * @return the header row
     * @throws IllegalAccessException -
     * @throws InstantiationException -
     */
    final HeaderRow setClassOnce(Class<?> clazz) throws IllegalAccessException, InstantiationException {
        setClass(clazz);
        this.t = clazz.newInstance();
        return this;
    }

    final Field[] getFields() {
        return fields;
    }

    final int[] getColumns() {
        return columns;
    }

    final Class<?>[] getFieldClazz() {
        return fieldClazz;
    }

    @SuppressWarnings("unchecked")
    final <T> T getT() {
        return (T) t;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    @Override
    public CellType getCellType(int columnIndex) {
        return CellType.STRING;
    }

    /**
     * Get the column name by column index
     *
     * @param columnIndex the cell index
     * @return name of column
     */
    public String get(int columnIndex) {
        rangeCheck(columnIndex);
        return names[columnIndex];
    }

    /**
     * Returns the position in cell range
     *
     * @param columnName the column name
     * @return the position if found otherwise -1
     */
    public int getIndex(String columnName) {
        Integer index = mapping.get(columnName);
        return index != null ? index : -1;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(" | ");
        StringBuilder buf = new StringBuilder();
        int i = 0;
        for (; names[i] == null; i++) ;
        char[] chars = new char[10];
        Arrays.fill(chars, 0, chars.length, '-');
        for (int j = i; i < names.length; i++) {
            joiner.add(names[i]);
            int n = simpleTestLength(names[i]) + (j == i || i == names.length - 1 ? 1 : 2);
            if (n > chars.length) {
                chars = new char[n];
                Arrays.fill(chars, 0, n, '-');
            } else {
                Arrays.fill(chars, 0, n, '-');
            }

            if (fieldClazz != null) {
                Class<?> c = fieldClazz[i];

                // Align Center
                if (isDate(c) || isLocalDate(c) || isLocalDateTime(c) || isLocalTime(c) || isChar(c) || isBool(c)) {
                    chars[0] = chars[n - 1] = ':';
                }
                // Align Right
                else if (isInt(c)) {
                    chars[n - 1] = ':';
                }
                // Align Left
//            else;
            }
            buf.append(chars, 0, n).append('|');

        }

        buf.insert(0, joiner.toString() + Const.lineSeparator);

        return buf.toString();
    }

    int simpleTestLength(String name) {
        if (name == null) return 4;
        char[] chars = name.toCharArray();
        double d = 0.0;
        for (char c : chars) {
            if (c < 0x80) d++;
            else d += 1.75;
        }
        return (int) d;
    }

    void put(Row row, Object t) throws IllegalAccessException, InvocationTargetException {
        for (int i = 0; i < columns.length; i++) {
            if (methods[i] != null)
                methodPut(i, row, t);
            else
                fieldPut(i, row, t);
        }
    }

    private void fieldPut(int i, Row row, Object t) throws IllegalAccessException {
        int c = columns[i];
        if (fieldClazz[i] == String.class) {
            fields[i].set(t, row.getString(c));
        } else if (fieldClazz[i] == int.class || fieldClazz[i] == Integer.class) {
            fields[i].set(t, row.getInt(c));
        } else if (fieldClazz[i] == long.class || fieldClazz[i] == Long.class) {
            fields[i].set(t, row.getLong(c));
        } else if (fieldClazz[i] == java.util.Date.class || fieldClazz[i] == java.sql.Date.class) {
            fields[i].set(t, row.getDate(c));
        } else if (fieldClazz[i] == java.sql.Timestamp.class) {
            fields[i].set(t, row.getTimestamp(c));
        } else if (fieldClazz[i] == double.class || fieldClazz[i] == Double.class) {
            fields[i].set(t, row.getDouble(c));
        } else if (fieldClazz[i] == float.class || fieldClazz[i] == Float.class) {
            fields[i].set(t, row.getFloat(c));
        } else if (fieldClazz[i] == boolean.class || fieldClazz[i] == Boolean.class) {
            fields[i].set(t, row.getBoolean(c));
        } else if (fieldClazz[i] == char.class || fieldClazz[i] == Character.class) {
            fields[i].set(t, row.getChar(c));
        } else if (fieldClazz[i] == byte.class || fieldClazz[i] == Byte.class) {
            fields[i].set(t, row.getByte(c));
        } else if (fieldClazz[i] == short.class || fieldClazz[i] == Short.class) {
            fields[i].set(t, row.getShort(c));
        }
    }

    private void methodPut(int i, Row row, Object t) throws IllegalAccessException, InvocationTargetException {
        int c = columns[i];
        if (fieldClazz[i] == String.class) {
            methods[i].invoke(t, row.getString(c));
        } else if (fieldClazz[i] == int.class || fieldClazz[i] == Integer.class) {
            methods[i].invoke(t, row.getInt(c));
        } else if (fieldClazz[i] == long.class || fieldClazz[i] == Long.class) {
            methods[i].invoke(t, row.getLong(c));
        } else if (fieldClazz[i] == java.util.Date.class || fieldClazz[i] == java.sql.Date.class) {
            methods[i].invoke(t, row.getDate(c));
        } else if (fieldClazz[i] == java.sql.Timestamp.class) {
            methods[i].invoke(t, row.getTimestamp(c));
        } else if (fieldClazz[i] == double.class || fieldClazz[i] == Double.class) {
            methods[i].invoke(t, row.getDouble(c));
        } else if (fieldClazz[i] == float.class || fieldClazz[i] == Float.class) {
            methods[i].invoke(t, row.getFloat(c));
        } else if (fieldClazz[i] == boolean.class || fieldClazz[i] == Boolean.class) {
            methods[i].invoke(t, row.getBoolean(c));
        } else if (fieldClazz[i] == char.class || fieldClazz[i] == Character.class) {
            methods[i].invoke(t, row.getChar(c));
        } else if (fieldClazz[i] == byte.class || fieldClazz[i] == Byte.class) {
            methods[i].invoke(t, row.getByte(c));
        } else if (fieldClazz[i] == short.class || fieldClazz[i] == Short.class) {
            methods[i].invoke(t, row.getShort(c));
        }
    }
}

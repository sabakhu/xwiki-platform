/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xpn.xwiki.internal.store.hibernate.query;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;

/**
 * Provide various SQL related utilities.
 * 
 * @version $Id$
 * @since 7.2M2
 */
public final class HqlQueryUtils
{
    private static final String DOCUMENT_FIELD_FULLNAME = "fullName";

    private static final String DOCUMENT_FIELD_NAME = "name";

    private static final String DOCUMENT_FIELD_SPACE = "space";

    private static final String DOCUMENT_FIELD_LANGUAGE = "language";

    private static final String DOCUMENT_FIELD_DEFAULTLANGUAGE = "defaultLanguage";

    private static final String DOCUMENT_FIELD_TRANSLATION = "translation";

    private static final String DOCUMENT_FIELD_HIDDEN = "hidden";

    private static final String SPACE_FIELD_REFERENCE = "reference";

    private static final String SPACE_FIELD_NAME = DOCUMENT_FIELD_NAME;

    private static final String SPACE_FIELD_PARENT = "parent";

    private static final String SPACE_FIELD_HIDDEN = DOCUMENT_FIELD_HIDDEN;

    private static final Map<String, Set<String>> ALLOWED_FIELDS;

    static {
        ALLOWED_FIELDS = new HashMap<>();

        Set<String> allowedDocFields = new HashSet<>();
        ALLOWED_FIELDS.put("XWikiDocument", allowedDocFields);
        allowedDocFields.add(DOCUMENT_FIELD_FULLNAME);
        allowedDocFields.add(DOCUMENT_FIELD_NAME);
        allowedDocFields.add(DOCUMENT_FIELD_SPACE);
        allowedDocFields.add(DOCUMENT_FIELD_LANGUAGE);
        allowedDocFields.add(DOCUMENT_FIELD_DEFAULTLANGUAGE);
        allowedDocFields.add(DOCUMENT_FIELD_TRANSLATION);
        allowedDocFields.add(DOCUMENT_FIELD_HIDDEN);

        Set<String> allowedSpaceFields = new HashSet<>();
        ALLOWED_FIELDS.put("XWikiSpace", allowedSpaceFields);
        allowedSpaceFields.add(SPACE_FIELD_REFERENCE);
        allowedSpaceFields.add(SPACE_FIELD_NAME);
        allowedSpaceFields.add(SPACE_FIELD_PARENT);
        allowedSpaceFields.add(SPACE_FIELD_HIDDEN);
    }

    private HqlQueryUtils()
    {

    }

    /**
     * @param statement the statement to evaluate
     * @return true if the statement is complete, false otherwise
     */
    public static boolean isShortFormStatement(String statement)
    {
        boolean isShortStatement = false;
        String lcStatement = statement.trim().toLowerCase();

        isShortStatement |= lcStatement.startsWith(", ");
        isShortStatement |= lcStatement.startsWith("from");
        isShortStatement |= lcStatement.startsWith("where");
        isShortStatement |= lcStatement.startsWith("order");

        return isShortStatement;
    }

    /**
     * @param statementString the SQL statement to check
     * @return true if the passed SQL statement is allowed
     */
    public static boolean isSafe(String statementString)
    {
        Statement statement;
        try {
            // TODO: should probably use a more specific Hql parser
            statement = CCJSqlParserUtil.parse(statementString);

            if (statement instanceof Select) {
                Select select = (Select) statement;

                SelectBody selectBody = select.getSelectBody();

                if (selectBody instanceof PlainSelect) {
                    PlainSelect plainSelect = (PlainSelect) selectBody;

                    Map<String, String> tables = getTables(plainSelect);

                    for (SelectItem selectItem : plainSelect.getSelectItems()) {
                        if (!isSelectItemAllowed(selectItem, tables)) {
                            return false;
                        }
                    }

                    return true;
                }
            }
        } catch (JSQLParserException e) {
            // We can't parse it so lets say it's not safe
        }

        return false;
    }

    private static Map<String, String> getTables(PlainSelect plainSelect)
    {
        Map<String, String> tables = new HashMap<>();

        // Add from item
        addFromItem(plainSelect.getFromItem(), tables);

        // Add joins
        List<Join> joins = plainSelect.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                addFromItem(join.getRightItem(), tables);
            }
        }

        return tables;
    }

    private static void addFromItem(FromItem item, Map<String, String> tables)
    {
        if (item instanceof Table) {
            String tableName = ((Table) item).getName();
            tables.put(item.getAlias() != null ? item.getAlias().getName() : tableName, tableName);
        }
    }

    /**
     * @param selectItem the {@link SelectItem} to check
     * @return true if the passed {@link SelectItem} is allowed
     */
    private static boolean isSelectItemAllowed(SelectItem selectItem, Map<String, String> tables)
    {
        if (selectItem instanceof SelectExpressionItem) {
            SelectExpressionItem selectExpressionItem = (SelectExpressionItem) selectItem;

            return isSelectExpressionAllowed(selectExpressionItem.getExpression(), tables);
        }

        // TODO: we could support more select items

        return false;
    }

    private static boolean isSelectExpressionAllowed(Expression expression, Map<String, String> tables)
    {
        if (expression instanceof Column) {
            Column column = (Column) expression;

            if (isColumnAllowed(column, tables)) {
                return true;
            }
        } else if (expression instanceof Function) {
            Function function = (Function) expression;

            for (Expression parameter : function.getParameters().getExpressions()) {
                if (!isSelectExpressionAllowed(parameter, tables)) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    /**
     * @param column the {@link Column} to check
     * @return true if the passed {@link Column} is allowed
     */
    private static boolean isColumnAllowed(Column column, Map<String, String> tables)
    {
        Set<String> fields = ALLOWED_FIELDS.get(getTableName(column.getTable(), tables));
        return fields != null && fields.contains(column.getColumnName());
    }

    private static String getTableName(Table table, Map<String, String> tables)
    {
        String tableName = tables.values().iterator().next();

        if (table != null && StringUtils.isNotEmpty(table.getFullyQualifiedName())) {
            tableName = tables.get(table.getFullyQualifiedName());
        }

        return tableName;
    }
}
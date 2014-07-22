/* This file is part of VoltDB.
 * Copyright (C) 2008-2014 VoltDB Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltdb.planner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.voltdb.catalog.Column;
import org.voltdb.catalog.Database;
import org.voltdb.catalog.Table;
import org.voltdb.expressions.AbstractExpression;
import org.voltdb.expressions.TupleValueExpression;
import org.voltdb.planner.parseinfo.StmtSubqueryScan;
import org.voltdb.planner.parseinfo.StmtTableScan;
import org.voltdb.planner.parseinfo.StmtTargetTableScan;
import org.voltdb.plannodes.AbstractPlanNode;
import org.voltdb.plannodes.SendPlanNode;

public class InsertSubPlanAssembler extends SubPlanAssembler {

    private boolean m_bestAndOnlyPlanWasGenerated = false;

    InsertSubPlanAssembler(Database db, AbstractParsedStmt parsedStmt,
            StatementPartitioning partitioning) {
        super(db, parsedStmt, partitioning);
        // TODO Auto-generated constructor stub
    }

    private static int countSendNodes(AbstractPlanNode node) {
        int count = 0;
        if (node instanceof SendPlanNode) {
            count++;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            count += countSendNodes(node.getChild(i));
        }

        return count;
    }

    @Override
    AbstractPlanNode nextPlan() {
        if (m_bestAndOnlyPlanWasGenerated) {
            return null;
        }

        // We may generate a few different plans for the subquery, but by the time
        // we get here, we'll generate only one plan for the INSERT statement itself.
        // Mostly this method exists to check that we can find a valid partitioning
        // for the statement.

        m_bestAndOnlyPlanWasGenerated = true;
        ParsedInsertStmt insertStmt = (ParsedInsertStmt)m_parsedStmt;
        Table targetTable = insertStmt.m_tableList.get(0);
        String targetTableName = targetTable.getTypeName();
        StmtSubqueryScan subquery = insertStmt.getSubquery();

        if (targetTable.getIsreplicated()) {
            // must not be single-partition insert if targeting a replicated table
            // setUpForNewPlans already validates this
            assert(! m_partitioning.wasSpecifiedAsSingle() && ! m_partitioning.isInferredSingle());

            // Cannot access any partitioned tables in subquery for replicated table
            if (! subquery.getIsReplicated()) {
                throw new PlanningErrorException("Subquery in INSERT INTO ... SELECT statement may not access " +
                                                 "partitioned data for insertion into replicated table " + targetTable.getTypeName() + ".");
            }
        }
        else if (! m_partitioning.wasSpecifiedAsSingle()) {
            //        [assume that c1 is the partitioning column]
            //        INSERT INTO t1 (c1, c2, c3, ...)
            //        SELECT e1, e2, e3, ... FROM ...
            //
            //        can be analyzed as if it was
            //
            //        SELECT COUNT(*)
            //        FROM t1
            //          INNER JOIN
            //            (SELECT e1, e2, e3, ... FROM ...) AS insert_subquery
            //            ON t1.c1 = insert_subquery.e1;
            //
            // Build the corresponding data structures for analysis by StatementPartitioning.

            if (countSendNodes(subquery.getBestCostPlan().rootPlanGraph) > 0) {
                // What is the appropriate level of detail for this message?
                m_recentErrorMsg = "INSERT INTO ... SELECT statement subquery is too complex.  " +
                    "Please either simplify the subquery or use a SELECT followed by an INSERT.";
                return null;
            }

            List<StmtTableScan> tables = new ArrayList<>();
            tables.add(new StmtTargetTableScan(targetTable, targetTable.getTypeName()));
            tables.add(subquery);

            // Create value equivalence between the partitioning column of the target table
            // and the corresponding expression produced by the subquery.

            HashMap<AbstractExpression, Set<AbstractExpression>>  valueEquivalence = new HashMap<>();
            int i = 0;
            Column partitioningCol = targetTable.getPartitioncolumn();
            boolean setEquivalenceForPartitioningCol = false;
            for (Column col : insertStmt.m_columns.keySet()) {
                if (partitioningCol.compareTo(col) == 0) {
                    TupleValueExpression tve = new TupleValueExpression(targetTableName, targetTableName, col.getName(), col.getName(), col.getIndex());
                    AbstractExpression selectedExpr = subquery.getOutputExpression(i);
                    assert(!valueEquivalence.containsKey(tve));
                    assert(!valueEquivalence.containsKey(selectedExpr));

                    Set<AbstractExpression> equivSet = new HashSet<>();
                    equivSet.add(tve);
                    equivSet.add(selectedExpr);

                    valueEquivalence.put(tve,  equivSet);
                    valueEquivalence.put(selectedExpr,  equivSet);
                    setEquivalenceForPartitioningCol = true;

                }
                ++i;
            }

            if (!setEquivalenceForPartitioningCol) {
                // partitioning column of target table is not being set from value produced by the subquery.
                m_recentErrorMsg = "Partitioning column must be assigned a value " +
                    "produced by the subquery in an INSERT INTO ... SELECT statement.";
                return null;
            }

            m_partitioning.analyzeForMultiPartitionAccess(tables, valueEquivalence);

            if (! m_partitioning.isJoinValid()) {
                m_recentErrorMsg = "Partitioning could not be determined for INSERT INTO ... SELECT statement.  " +
                    "Please ensure that statement does not attempt to copy row data from one partition to another, " +
                    "which is unsupported.";
                return null;
            }
        }


        return subquery.getBestCostPlan().rootPlanGraph;
    }

}

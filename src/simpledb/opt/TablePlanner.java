package simpledb.opt;

import java.util.Map;
import simpledb.tx.Transaction;
import simpledb.record.*;
import simpledb.query.*;
import simpledb.metadata.*;
import simpledb.index.planner.*;
import simpledb.materialize.HashJoinPlan;
import simpledb.materialize.MergeJoinPlan;
import simpledb.materialize.NestedJoinPlan;
import simpledb.multibuffer.MultibufferProductPlan;
import simpledb.plan.*;

/**
 * This class contains methods for planning a single table.
 * @author Edward Sciore
 */
class TablePlanner {
   private TablePlan myplan;
   private Predicate mypred;
   private Schema myschema;
   private Map<String,IndexInfo> indexes;
   private Transaction tx;

   /**
    * Creates a new table planner.
    * The specified predicate applies to the entire query.
    * The table planner is responsible for determining
    * which portion of the predicate is useful to the table,
    * and when indexes are useful.
    * @param tblname the name of the table
    * @param mypred the query predicate
    * @param tx the calling transaction
    */
   public TablePlanner(String tblname, Predicate mypred, Transaction tx, MetadataMgr mdm) {
      this.mypred  = mypred;
      this.tx  = tx;
      myplan   = new TablePlan(tx, tblname, mdm);
      myschema = myplan.schema();
      indexes  = mdm.getIndexInfo(tblname, tx);
   }

   /**
    * Constructs a select plan for the table.
    * The plan will use an indexselect, if possible.
    * @return a select plan for the table.
    */
   public Plan makeSelectPlan() {
      Plan p = makeIndexSelect();
      if (p == null)
         p = myplan;
      return addSelectPred(p);
   }

   /**
    * Constructs a join plan of the specified plan
    * and the table.  The plan will use an indexjoin, if possible.
    * (Which means that if an indexselect is also possible,
    * the indexjoin operator takes precedence.)
    * The method returns null if no join is possible.
    * @param current the specified plan
    * @return a join plan of the plan and this table
    */
   public Plan makeJoinPlan(Plan current) {
      Schema currsch = current.schema();
      Predicate joinpred = mypred.joinSubPred(myschema, currsch);
      if (joinpred == null)
         return null;

      Plan p = makeHashJoin(current, currsch);
      if (p == null)
          p = makeIndexJoin(current, currsch);
      if (p == null)
    	  p = makeMergeJoin(current, currsch);
      if (p == null)
          p = makeNestedJoin(current, currsch);
      if (p == null)
          p = makeProductJoin(current, currsch);

      System.out.println("Join cost: " + p.blocksAccessed());
      return p;
   }

   private Plan makeNestedJoin(Plan current, Schema currsch) {
	   for (String fldname: myschema.fields()) {
            String outerfield = mypred.equatesWithField(fldname);
            if (outerfield != null && currsch.hasField(outerfield)) {
                System.out.println("Using nested join plan...");
                Plan p = new NestedJoinPlan(current, myplan, outerfield, fldname);
                p = addSelectPred(p);
                return addJoinPred(p, currsch);
            }
        }
        return null;
    }

   private Plan makeHashJoin(Plan current, Schema currsch) {
	   for (String fldname: myschema.fields()) {
           String outerfield = mypred.equatesWithField(fldname);
           if (outerfield != null && currsch.hasField(outerfield)) {
        	   Plan p = new HashJoinPlan(current, myplan, outerfield, fldname, tx);
        	   p = addSelectPred(p);
			    System.out.println("Using hash join plan...");
			    return addJoinPred(p, currsch);
           }
	   }
	   return null;
   }

   /**
    * Constructs a sort-merge join plan
    */
   private Plan makeMergeJoin(Plan current, Schema currsch) {
	   String plan1Field = null;
	   String plan2Field = null;
	   for (String field : currsch.fields()) {
		   Predicate queryPreds = mypred;
		   plan2Field = queryPreds.equatesWithField(plan1Field = field);

		   if (plan2Field != null)
			   break;
	   }

	   if (plan2Field == null || plan1Field == null) return null;

	   System.out.println("Using merge join plan...");
	   return new MergeJoinPlan(tx, current, myplan, plan1Field+"-"+"asc", plan2Field+"-"+"asc");
   }

   /**
    * Constructs a product plan of the specified plan and
    * this table.
    * @param current the specified plan
    * @return a product plan of the specified plan and this table
    */
   public Plan makeProductPlan(Plan current) {
      Plan p = addSelectPred(myplan);
      return new MultibufferProductPlan(tx, current, p);
   }

   private Plan makeIndexSelect() {
      for (String fldname : indexes.keySet()) {
         Constant val = mypred.equatesWithConstant(fldname);
         if (val != null) {
            IndexInfo ii = indexes.get(fldname);
            System.out.println("index on " + fldname + " used");
            return new IndexSelectPlan(myplan, ii, val);
         }
      }
      return null;
   }

   private Plan makeIndexJoin(Plan current, Schema currsch) {
      for (String fldname : indexes.keySet()) {
         String outerfield = mypred.equatesWithField(fldname);
         if (outerfield != null && currsch.hasField(outerfield)) {
            IndexInfo ii = indexes.get(fldname);
            Plan p = new IndexJoinPlan(current, myplan, ii, outerfield);
            p = addSelectPred(p);
            System.out.println("Using index join plan...");
            return addJoinPred(p, currsch);
         }
      }
      return null;
   }

   private Plan makeProductJoin(Plan current, Schema currsch) {
       System.out.println("Using product join...");
      Plan p = makeProductPlan(current);
      return addJoinPred(p, currsch);
   }

   private Plan addSelectPred(Plan p) {
      Predicate selectpred = mypred.selectSubPred(myschema);
      if (selectpred != null)
         return new SelectPlan(p, selectpred);
      else
         return p;
   }

   private Plan addJoinPred(Plan p, Schema currsch) {
      Predicate joinpred = mypred.joinSubPred(currsch, myschema);
      if (joinpred != null)
         return new SelectPlan(p, joinpred);
      else
         return p;
   }
}

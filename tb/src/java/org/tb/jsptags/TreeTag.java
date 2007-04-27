package org.tb.jsptags;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.TagSupport;

import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Suborder;
import org.tb.logging.TbLogger;

	/**
	 * This class generates a tree view for jsp-Pages.  
	 * The sourcecode is based on the tree-Tag-implementation of Guy Davis.
	 * See http://www.guydavis.ca/projects/oss/tags/ for more information.
	 * Use this code with a matching "*.tld"-file.  
	 * 
	 * @author ts
	 */
public class TreeTag extends TagSupport {
	
    private String browser = null;
    private String changeFunctionString = "";
    private String deleteFunctionString = "";
    private Customerorder mainProject;
    private List<Suborder> subProjects;
    private String defaultString;
    private Long currentSuborderID;
    private List<Long> internalNodesIDs;
    private static int painted = 0;
    private Boolean onlySuborders = false;
    
    private static Random rand = null;

	public void setDeleteFunctionString(String deleteFunctionString) {
		this.deleteFunctionString = deleteFunctionString;
	}

	public void setOnlySuborders(Boolean onlySuborders) {
		this.onlySuborders = onlySuborders;
	}

	public void setCurrentSuborderID(Long currentSuborderID) {
		this.currentSuborderID = currentSuborderID;
	}

	public void setDefaultString(String defaultString) {
		this.defaultString = defaultString;
	}

	public void setMainProject(Customerorder mainProject) {
		this.mainProject = mainProject;
	}

	public void setSubProjects(List<Suborder> subProjects) {
		this.subProjects = subProjects;
	}

	public void setBrowser(String browser) {
        this.browser = browser;
    }
    
    public void setChangeFunctionString(String changeFunctionString) {
		this.changeFunctionString = changeFunctionString;
	}

	public int doStartTag() {
        JspWriter out = pageContext.getOut();
        return (doStartTag(out));
    }

	/**
	 * This methode is called from the jsp to produce some output on it.
	 * In this case, the root and all children must be created for an order.
	 * The methode does the steps in the following order:
	 *  - prepaire the jsp and some values (randomizer and function name on jsp)
	 * 	- prepare entries which should be printed bold
	 * 	- prepaire entries which are no parents (leafs)
	 *  - build the root of the tree view
	 *  - build all children recursivly
	 *  
	 * @param out
	 * @return
	 */
    public int doStartTag(JspWriter out) {
        try {
        	
        	painted++;
            printScript(out);
            if (rand == null)
                rand = new Random();
            String name = Integer.toString(rand.nextInt()); 
            String tempChangeFunctionString = changeFunctionString.replaceFirst(this.defaultString, mainProject.getId() + "");
            
            //-----------------------------------------------
            //		prepaire entries which are no parents (leafs)
            //-----------------------------------------------
            this.internalNodesIDs = new ArrayList<Long>();
            for (int i=0; i<this.subProjects.size();i++){
            	if (this.subProjects.get(i).getParentorder()!=null)
            		this.internalNodesIDs.add(new Long(this.subProjects.get(i).getParentorder().getId()));
            }
            //------------------------------------------------
            // 		print the root of the tree view
            //------------------------------------------------
            out.println("<TABLE BORDER=0 cellspacing=\"0\" cellpadding=\"0\"><tr>");
 			out.println( "<td class=\"noBborderStyle\" nowrap width=\"30\" align=\"left\"> <img id=\"img" + name + "\" src=\"" + GlobalConstants.ICONPATH + GlobalConstants.CLOSEICON + "\" border=\"0\" " );
			out.println( " onClick=\"nodeClick(event, this, '" + name + "', '" + GlobalConstants.CLOSEICON + "', '"+ GlobalConstants.OPENICON + "');\"></td>");
			//out.println("<td class=\"noBborderStyle\" nowrap width=\"30\" align=\"left\"><img src=\""+ GlobalConstants.ICONPATH + img_folder + "\"</img></td>");
			if (onlySuborders != true 
					&& this.changeFunctionString!=null 
					&& !this.changeFunctionString.equals("")){
				out.print( "<td class=\"noBborderStyle\" nowrap align=\"left\">" + mainProject.getSignAndDescription() + "</td>");
				out.println("<td class=\"noBborderStyle\" nowrap align=\"left\"> <input type=\"image\" name= \"\"  src=\"" + GlobalConstants.ICONPATH + GlobalConstants.PARENTICON + "\" border=\"0\" " );
				out.println(" onclick=\"" + tempChangeFunctionString + "\";></td>");
			
			}else{
				out.print( "<td class=\"noBborderStyle\" nowrap align=\"left\"> " + mainProject.getSignAndDescription());
			}
				
			out.print(" </td>");
			out.print( "\n</tr>" );    
			out.print( "</table>\n");
			out.println( "<span id=\"span" + name + "\" class=\"clsHide\">\n");
			//------------------------------------------------
			// 		the tree view with all the children
			//------------------------------------------------
			if (this.subProjects != null){
				generateTreeRecursivly(mainProject.getId(), 0, out, true); 
			} 
			out.print("</span>");
        } catch(Exception ioe) {
        	TbLogger.getLogger().error("Error in Tree Tag!");
        }
        return(EVAL_BODY_INCLUDE);
    }
    
	/**
	 *  This methode generates the treestructure of the object recursivly.
	 *  Normaly this methode should be called with a toplevel order ID for the parentID.
	 *  Then the methode builds the rest of the tree recursivly by calling it self several times.
	 *  
	 * @param parentID   ID of the parent (node before actual nodes)
	 * @param lastLevel  level (in the tree) of the parent
	 * @param outPut     chanel for output on jsp
	 * @param enabled	 boolean which defines whether the actual node can be used as parent or not
	 * 					 (This is to prevent cyclic dependencies, e.g. when a node is edited and his 
	 * 					 position in the tree changes, the new parent must not be a child, else we have a deadlock)
	 */
    private void generateTreeRecursivly(long parentID, int lastLevel, JspWriter outPut, boolean enabled) {
		int thisLevel = lastLevel + 1;  // thisLevel is the level for all children
		
    	for (int i=0;i<subProjects.size();i++){
			Suborder tempOrder = subProjects.get(i);
			// testing, if there are any children for this node:
			if ((tempOrder.getParentorder() != null 
					&& tempOrder.getParentorder().getId() == parentID)  // -->  This case is a leaf which has a suborder as parent
					|| (tempOrder.getParentorder() == null
							&& tempOrder.getCustomerorder().getId() == parentID  // -->  This case is leaf which has the main project as parent
							&& thisLevel == 1)){
				// some things must be prepaired
				boolean tempBoolean = true;
				if (enabled == false || this.currentSuborderID == tempOrder.getId())
						tempBoolean = false; 
				TbLogger.getLogger().debug("Logging for enabled:  " + enabled + " "+this.currentSuborderID +" " + " "+   tempOrder.getId());
				String name = Integer.toString(rand.nextInt());  
				String tempChangeFunctionString = changeFunctionString.replaceFirst(this.defaultString, tempOrder.getId() + "");
				String tempDeleteFunctionString = deleteFunctionString.replaceFirst(this.defaultString, tempOrder.getId() + "");
				
				StringBuffer sb = new StringBuffer();
				sb.append(tempOrder.getSignAndDescription() + "; [");
				if (tempOrder.getFromDate()!=null)
					sb.append(tempOrder.getFromDate() + ", ");
				else
					sb.append(" - , ");
				if (tempOrder.getUntilDate()!=null)
					sb.append(tempOrder.getUntilDate() + "]; ");
				else
					sb.append(" - ]; ");
				sb.append(tempOrder.getHourly_rate() + " " + tempOrder.getCurrency()  + "; ");
				if (tempOrder.getDebithours()!=null)
					sb.append(tempOrder.getDebithours() );
				else
					sb.append("-" );
				String buttonText = sb.toString();
				
				// prepaire all nodes for the way from root to actual subproject			
				try{
					outPut.println("<TABLE BORDER=0 cellspacing=\"0\" cellpadding=\"0\"><tr>");
					outPut.println("<td class=\"noBborderStyle\" nowrap width=\"" + (30+ 37 * lastLevel) + "\">&nbsp;</td>" );
					//check, if the (+/-)-Sign must be printed or if this node is a leaf node
					if (this.internalNodesIDs.contains(new Long(tempOrder.getId()))){
						outPut.println("<td class=\"noBborderStyle\" nowrap width=\"30\"> <img id=\"img" + name + "\" src=\"" + GlobalConstants.ICONPATH + GlobalConstants.CLOSEICON + "\" border=\"0\" " );
						outPut.println("onClick=\"nodeClick(event, this, '" + name + "', '" + GlobalConstants.CLOSEICON + "', '"+ GlobalConstants.OPENICON + "');\"></td>");
						//outPut.println("<td class=\"noBborderStyle\" nowrap width=\"30\"><img src=\""+ GlobalConstants.ICONPATH + img_folder + "\"</img></td>");  
					} else {
						outPut.println("<td class=\"noBborderStyle\" nowrap width=\"30\">&nbsp;</td>" );
						//outPut.println("<td class=\"noBborderStyle\" nowrap width=\"30\"><img src=\""+ GlobalConstants.ICONPATH + img_folder + "\"</img></td>");  
					}

		  			outPut.println("<td class=\"noBborderStyle\" nowrap align=\"left\">" +  buttonText + "</td>");
					
					if (tempChangeFunctionString.length() > 0
							&& tempBoolean
							&& tempDeleteFunctionString.length() >0 ){
						outPut.println("<td class=\"noBborderStyle\" nowrap align=\"left\"> <input type=\"image\" name= \"\"  src=\"" + GlobalConstants.ICONPATH + GlobalConstants.EDITICON + "\" border=\"0\" " );
						outPut.println(" onclick=\"" + tempChangeFunctionString + "\";></td>");
					} else if (tempChangeFunctionString.length() > 0
							&& tempBoolean
							&& tempDeleteFunctionString.length() == 0 ){
						outPut.println("<td class=\"noBborderStyle\" nowrap align=\"left\"> <input type=\"image\" name= \"\"  src=\"" + GlobalConstants.ICONPATH + GlobalConstants.PARENTICON + "\" border=\"0\" " );
						outPut.println(" onclick=\"" + tempChangeFunctionString + "\";></td>");
					} else{
						outPut.println("<td class=\"noBborderStyle\" nowrap align=\"left\"> <img id=\"img1\" src=\"" + GlobalConstants.ICONPATH + GlobalConstants.NOTALLOWED + "\" border=\"0\" </td>");
					}
					
					if (tempDeleteFunctionString.length() >0 
							&& tempBoolean){	
						outPut.println("<td class=\"noBborderStyle\" nowrap align=\"left\"> <input type=\"image\" name= \"\"  src=\"" + GlobalConstants.ICONPATH + GlobalConstants.DELETEICON + "\" border=\"0\" " );
						outPut.println(" onclick=\"" + tempDeleteFunctionString + "\";></td>");
					}
					
		  			outPut.println("\n</tr></TABLE>\n" );  
		  			outPut.println("<span id=\"span" + name + "\" class=\"clsHide\">\n");
		  			generateTreeRecursivly(tempOrder.getId(), thisLevel, outPut, tempBoolean );
		  			outPut.println("</span>");
				}catch (IOException ioe){	
		        	TbLogger.getLogger().error("Error in Tree Tag!");
				} 
			}
		}
	}

	public int doEndTag() {
        JspWriter out = pageContext.getOut();
        return (doEndTag(out));
    }
    
    public int doEndTag(JspWriter out) {
        try {
        	out.print("");
        } catch(IOException ioe) {
            System.out.println("Error in TreeTag: " + ioe);
        }
        return(EVAL_PAGE); 
    }

    /**
     *  Outputs the correct Javascript to generate dynamic tree views of listings.
     *  The correct code depends on the browser used by the client.
     */
    private void printScript(JspWriter out) throws IOException {
        out.println("<script language='JavaScript'>");
        out.println("function nodeClick( evt, eSrc, id, open, closed ) { ");
        // Netscape and Mozilla specific Javascript
        if ((browser != null) && (browser.indexOf("MSIE") < 0))  {
            out.println("evt.stopPropagation();");
            out.println("var eSpan = document.getElementById('span'+id);");
            out.println("eSpan.className = (eSpan.className=='clsShow') ? 'clsHide' : 'clsShow';");
            out.println("var eImg = document.getElementById('img'+id);");
        } else {  // Microsoft Internet Explorer
            out.println("window.event.cancelBubble = true;");
            out.println("var eSpan = document.all['span'+id];");
            out.println("eSpan.className = (eSpan.className=='clsShow') ? 'clsHide' : 'clsShow';");
            out.println("var eImg = document.all['img'+id];");
        }
        out.println("if( eSpan.className=='clsHide' ) {");
        out.println("eImg.src = ('"+GlobalConstants.ICONPATH+"' + open);");
        out.println("} else {");
        out.println("eImg.src = ('"+GlobalConstants.ICONPATH+"' + closed);");
        out.println("}");
        out.println("} </script>");
        out.println("<style type='text/css'>");
        out.println("   .clsShow { }");
        out.println("   .clsHide { display: none; }");
        out.println("</style>");
    }

}


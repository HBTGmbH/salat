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

	/**
	 * This class generates a tree view for jsp-Pages.  
	 * The sourcecode is based on the tree-Tag-implementation of Guy Davis.
	 * See http://www.guydavis.ca/projects/oss/tags/ for more information.
	 * 
	 * Use this code with a matching "*.tld"-file.  
	 * 
	 * @author ts
	 *
	 */
public class TreeTag extends TagSupport {
	
    private final String img_closed = "plus_circle.gif";
    private final String img_active = "minus_circle.gif"; 
    private String browser = null;
    private String changeFunctionString = "";
    private Customerorder mainProject;
    private List<Suborder> subProjects;
    private String defaultString;
    private Long currentSuborderID;
    private List<Long> boldIDs;
    
    private static Random rand = null;
    


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
	 *    This methode is called from the jsp to produce some output on it.
	 *    In this case, the root and all children must be created for an order.
	 *    First we build the root and then all children recursivly.
	 * @param out
	 * @return
	 */
    public int doStartTag(JspWriter out) {
        try {
            printScript(out);
            if (rand == null)
                rand = new Random();
            String name = Integer.toString(rand.nextInt()); 
            String tempChangeFunctionString = changeFunctionString.replaceFirst(this.defaultString, mainProject.getId() + "");
            //prepaire entries which should be printed bold:
            
            long toTestId = this.currentSuborderID.longValue();
            this.boldIDs = new ArrayList<Long>();
            if (this.currentSuborderID != null){
            	this.boldIDs.add(new Long(this.mainProject.getId()));
            	this.boldIDs.add(this.currentSuborderID);
                int level = 0;
                while (toTestId != this.mainProject.getId()
                		&& level <= 20){  // breaking condition maximized by level 20 of the tree, to prevent endless loops
                	  for (int i=0 ; i < this.subProjects.size() ; i++){
                      	if (this.subProjects.get(i).getId() == toTestId){
                      		this.boldIDs.add(new Long(toTestId));
                      		toTestId = new Long(this.subProjects.get(i).getParentorderid());
                      		break;
                      	}
                      }
                	  level++;
                }
            }
            	
            
            // 1. the root of the tree view:
            out.println("<TABLE BORDER=0 cellspacing=\"0\" cellpadding=\"0\"><tr>");
 			out.println( "<td class=\"noBborderStyle\" nowrap width=\"25\"> <img id=\"img" + name + "\" src=\"" + GlobalConstants.ICONPATH + img_closed + "\" border=\"0\" " );
			out.println( " onClick=\"nodeClick(event, this, '" + name + "', '" + img_closed + "', '"+ img_active + "');\"></td>");  
			out.print( "<td class=\"noBborderStyle\" nowrap ><b>" + mainProject.getSignAndDescription() + "</b></td>" 	 );
			out.print( "<td class=\"noBborderStyle\" nowrap align=\"right\" width=\"25\">"
					+ "<input type=\"submit\" value=\"-->\" onclick=\"" + tempChangeFunctionString + "\" id=\"button\">" );
			out.print( "\n</tr>" );    
			out.print( "</table>\n");
			out.println( "<span id=\"span" + name + "\" class=\"clsHide\">\n");
			// 2. the tree view with all the children:
			if (this.subProjects != null){
				out.println("<TABLE BORDER=0 cellspacing=\"0\" cellpadding=\"0\"><tr>");
				generateTreeRecursivly(mainProject.getId(), 0, out); 
				out.println("</TABLE>");
			} else{
				out.println("<input type=\"submit\" VALUE=\"subProjects==null\" >");	
			}
			out.print("</span>");
        } catch(Exception ioe) {
            System.out.println("Error in TreeTag: " + ioe);
        }
        return(EVAL_BODY_INCLUDE);
    }
    
	/**
	 *  This methode generates the treestructure of the object recursivly.
	 *  
	 * @param parentID   ID of the parent (noder before actual nodes)
	 * @param lastLevel  level of the parent
	 * @param outPut     chanel for output
	 */
    private void generateTreeRecursivly(long parentID, int lastLevel, JspWriter outPut) {
		int thisLevel = lastLevel + 1;  // thisLevel is the level for all children
    	for (int i=0;i<subProjects.size();i++){
			Suborder tempOrder = subProjects.get(i);
			// testing, if there are any children for this node:
			if (tempOrder.getParentorderid() == parentID){
				// some things must be prepaired
				String name = Integer.toString(rand.nextInt());  
				String tempChangeFunctionString = changeFunctionString.replaceFirst(this.defaultString, tempOrder.getId() + "");
				// prepaire all nodes for the way from root to actual subproject			
				try{
					outPut.println("<TABLE BORDER=0 cellspacing=\"0\" cellpadding=\"0\"><tr>");
					outPut.println("<td class=\"noBborderStyle\" nowrap width=\"" + 50 * thisLevel + "\">&nbsp;</td>" );
					outPut.println("<td class=\"noBborderStyle\" nowrap width=\"25\"> <img id=\"img" + name + "\" src=\"" + GlobalConstants.ICONPATH + img_closed + "\" border=\"0\" " );
					outPut.println("onClick=\"nodeClick(event, this, '" + name + "', '" + img_closed + "', '"+ img_active + "');\"></td>");  
		  			if (this.boldIDs.contains(new Long(tempOrder.getId()))){ // print it bold, when its on the treepath:
		  				outPut.println("<td class=\"noBborderStyle\"" + " nowrap " + "><b>" + tempOrder.getSignAndDescription() + "</b></td>" );	
		  			}else{ // not bold
		  				outPut.println("<td class=\"noBborderStyle\"" + " nowrap " + ">" + tempOrder.getSignAndDescription() + "</td>" );	
		  			}
					if (tempOrder.getId() != this.currentSuborderID.longValue())
						outPut.println("<td class=\"noBborderStyle\" nowrap align=\"right\" width=\"25\"><input type=\"submit\" value=\"-->\" onclick=\"" + tempChangeFunctionString + "\" id=\"button\">" );
		  			outPut.println("\n</tr></TABLE>\n" );  
		  			outPut.println("<span id=\"span" + name + "\" class=\"clsHide\">\n");
		  			generateTreeRecursivly(tempOrder.getId(), thisLevel, outPut);
		  			outPut.println("</span>");
				}catch (IOException ioe){
					System.out.println("Error in TreeTag: " + ioe);
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
        // Now output the required CSS for hiding stuff
        out.println("<style type='text/css'>");
        out.println("   .clsShow { }");
        out.println("   .clsHide { display: none; }");
        out.println("</style>");
    }

}


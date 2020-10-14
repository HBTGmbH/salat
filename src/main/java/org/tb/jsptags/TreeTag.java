package org.tb.jsptags;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Suborder;

import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.TagSupport;
import java.io.IOException;
import java.util.*;

/**
 * This class generates a tree view for jsp-Pages.
 * The sourcecode is based on the tree-Tag-implementation of Guy Davis.
 * See http://www.guydavis.ca/projects/oss/tags/ for more information.
 * Use this code with a matching "*.tld"-file.
 * Note that the generated JSP-Tag needs specific information as defined in the tld file.
 *
 * @author ts
 */
public class TreeTag extends TagSupport {
    private static final Logger LOG = LoggerFactory.getLogger(TreeTag.class);

    /**
     *
     */
    private static final long serialVersionUID = 1L; // 8705101629331486419L;
    // Random number needed for imag-label-generating.
    private static Random rand = new Random();
    // The name of the browser used by the client.
    private String browser = null;
    // this is the function (java script) specified by a string,
    // which should be called when the EDIT-Button is clicked
    private String changeFunctionString = "";
    // this is the function (java script) specified by a string,
    // which should be called when the DELETE-Button is clicked
    private String deleteFunctionString = "";
    // The Root of the tree structure, represented by a customer entity
    private Customerorder mainProject;
    // a list of all suborders that must be used for building the tree structure
    private List<Suborder> subProjects;
    // when the change- and/or the delete function string is used, this might contain
    // a "default string" which can be replaced by other content.  The default String
    // is stored in this variable:
    private String defaultString;
    // The ID of the current suborder chosen by the client
    private Long currentSuborderID;
    // The internal nodes need an icon for tree browsing.  This variable will be filled
    // in the "doStartTag"-Methode by iterating all suborders checking, whether they are a parent node
    // or not.
    private List<Long> internalNodesIDs;
    // This boolean specifies whether the "changeFunctionString" should be called only by clicking
    // on an suborder icon or even by clicking on the order (root) icon
    private Boolean onlySuborders = false;
    // The string represents the text for open dates.
    private String endlessDate = "-";

    // ------------------------------------------------
    // All set-methods for communicating with the jsp!
    // ------------------------------------------------
    public void setEndlessDate(String endlessDate) {
        this.endlessDate = endlessDate;
    }

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

    /**
     * This methode is called from the jsp to produce some output on it.
     * In this case, the root and all children must be created for an order.
     * The methode does the steps in the following order:
     * - prepare the jsp and some values (randomizer and function name on jsp)
     * - prepare entries which should be printed bold
     * - prepare entries which are no parents (leafs)
     * - build the root of the tree view
     * - build all children recursivly
     *
     * @param out
     * @return
     */
    public int doStartTag(JspWriter out) {
        try {
            printScript(out);
            String name = Integer.toString(rand.nextInt());

            //-----------------------------------------------
            //		prepare entries which are no parents (leafs)
            //-----------------------------------------------
            this.internalNodesIDs = new ArrayList<Long>();
            if (this.subProjects != null) {
                for (Suborder suborder : this.subProjects) {
                    if (suborder.getParentorder() != null) {
                        this.internalNodesIDs.add(new Long(suborder.getParentorder().getId()));
                    }
                }
            }
            //------------------------------------------------
            // 		print the root of the tree view
            //------------------------------------------------
            out.println("<TABLE BORDER=0 cellspacing=\"0\" cellpadding=\"0\"><tr>");
            out.print("<td class=\"noBborderStyle\" nowrap width=\"30\" align=\"left\"> <img id=\"img");
            out.print(name);
            out.print("\" src=\"");
            out.print(GlobalConstants.ICONPATH);
            out.print(GlobalConstants.CLOSEICON);
            out.println("\" border=\"0\" ");
            out.print(" onClick=\"nodeClick(event, this, '");
            out.print(name);
            out.print("', '");
            out.print(GlobalConstants.CLOSEICON);
            out.print("', '");
            out.print(GlobalConstants.OPENICON);
            out.println("');\"></td>");
            if (!onlySuborders && this.changeFunctionString != null && !this.changeFunctionString.isEmpty()) {
                String tempChangeFunctionString = changeFunctionString.replaceFirst(this.defaultString, Long.toString(mainProject.getId()));
                out.print("<td class=\"noBborderStyle\" nowrap align=\"left\"><b>" + mainProject.getSignAndDescription() + "</b></td>");
                out.println("<td class=\"noBborderStyle\" nowrap align=\"left\"> <input type=\"image\" name= \"\"  src=\"" + GlobalConstants.ICONPATH + GlobalConstants.PARENTICON + "\" border=\"0\" ");
                out.println(" onclick=\"" + tempChangeFunctionString + "\";></td>");

            } else {
                out.print("<td class=\"noBborderStyle\" nowrap align=\"left\"> <b>" + mainProject.getSignAndDescription() + "</b>");
            }
            out.print(" </td>");
            out.print("\n</tr>");
            out.print("</table>\n");
            out.println("<span id=\"span" + name + "\" class=\"clsHide\">\n");
            //------------------------------------------------
            // 		the tree view with all the children
            //------------------------------------------------
            if (this.subProjects != null) {
                generateTreeRecursivly(mainProject.getId(), 0, out, true, fillFilteredHierarchy(subProjects));
            }
            out.print("</span>");
        } catch (Exception ioe) {
            LOG.error("Error in Tree Tag!");
        }
        return (EVAL_BODY_INCLUDE);
    }

    /**
     * helping methode which references the one above
     */
    public int doStartTag() {
        JspWriter out = pageContext.getOut();
        return doStartTag(out);
    }

    /**
     * SALAT-614
     * for a filtered list of suborders, produces one with the parent suborders included
     *
     * @param input
     * @return
     */
    private Collection<Suborder> fillFilteredHierarchy(Collection<Suborder> input) {
        Collection<Suborder> result = new LinkedHashSet<Suborder>();
        for (Suborder suborder : input) {
            Suborder parent = suborder;
            while (parent != null) {
                result.add(parent);
                parent = parent.getParentorder();
            }
        }
        return result;
    }

    /**
     * This methode generates the treestructure of the object recursivly.
     * Normaly this methode should be called with a toplevel order ID for the parentID.
     * Then the methode builds the rest of the tree recursivly by calling it self several times.
     *
     * @param parentID  ID of the parent (node before actual nodes)
     * @param lastLevel level (in the tree) of the parent
     * @param outPut    chanel for output on jsp
     * @param enabled   boolean which defines whether the actual node can be used as parent or not
     *                  (This is to prevent cyclic dependencies, e.g. when a node is edited and his
     *                  position in the tree changes, the new parent must not be a child, else we have a deadlock)
     */
    private void generateTreeRecursivly(long parentID, int lastLevel, JspWriter outPut, boolean enabled, Collection<Suborder> filteredHierarchy) {
        int thisLevel = lastLevel + 1;  // thisLevel is the level for all children

        for (Suborder suborder : filteredHierarchy) {
            // testing, if there are any children for this node:
            if ((suborder.getParentorder() != null
                    && suborder.getParentorder().getId() == parentID)  // -->  This case is a leaf which has a suborder as parent
                    || (suborder.getParentorder() == null
                    && suborder.getCustomerorder().getId() == parentID  // -->  This case is leaf which has the main project as parent
                    && thisLevel == 1)) {
                // some things must be prepaired
                String colorForInvalidSubs = suborder.getCurrentlyValid() ? "" : " style=\"color:gray\" ";
                //--------------------------------------------------------------------------------
                //
                //     This is the part which must changed to realize different views for valid
                //     and deprecated suborders!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                //
                //--------------------------------------------------------------------------------
                boolean editable = enabled && (this.currentSuborderID == null || this.currentSuborderID != suborder.getId());
                String name = Integer.toString(rand.nextInt());
                String workingChangeFunctionStr = changeFunctionString.replaceFirst(this.defaultString, Long.toString(suborder.getId()));
                String workingDeleteFunctionStr = deleteFunctionString.replaceFirst(this.defaultString, Long.toString(suborder.getId()));

                StringBuffer sb = new StringBuffer();
                sb.append(suborder.getSignAndDescription()).append("; [");
                if (suborder.getFromDate() != null) {
                    sb.append(suborder.getFromDate());
                } else {
                    sb.append(" ").append(endlessDate);
                }
                sb.append(", ");
                if (suborder.getUntilDate() != null) {
                    sb.append(suborder.getUntilDate());
                } else {
                    sb.append(" ").append(endlessDate);
                }
                sb.append("]; ").append(suborder.getHourly_rate()).append(" ").append(suborder.getCurrency()).append("; ");
                if (suborder.getDebithours() != null) {
                    sb.append(suborder.getDebithours());
                } else {
                    sb.append("-");
                }
                String buttonText = sb.toString();

                // prepaire all nodes for the way from root to actual subproject
                try {
                    outPut.println("<TABLE BORDER=0 cellspacing=\"0\" cellpadding=\"0\"><tr>");
                    outPut.println("<td class=\"noBborderStyle\" nowrap width=\"" + (30 + 37 * lastLevel) + "\">&nbsp;</td>");
                    //check, if the (+/-)-Sign must be printed or if this node is a leaf node
                    if (this.internalNodesIDs.contains(new Long(suborder.getId()))) {
                        outPut.println("<td class=\"noBborderStyle\" nowrap width=\"30\"> <img id=\"img" + name + "\" src=\"" + GlobalConstants.ICONPATH + GlobalConstants.CLOSEICON + "\" border=\"0\" ");
                        outPut.println("onClick=\"nodeClick(event, this, '" + name + "', '" + GlobalConstants.CLOSEICON + "', '" + GlobalConstants.OPENICON + "');\"></td>");
                    } else {
                        outPut.println("<td class=\"noBborderStyle\" nowrap width=\"30\">&nbsp;</td>");
                    }
                    outPut.println("<td class=\"noBborderStyle\"  " + colorForInvalidSubs + " nowrap align=\"left\"><b>" + buttonText + "</b></td>");
                    if (workingChangeFunctionStr.length() > 0
                            && editable
                            && workingDeleteFunctionStr.length() > 0) {
                        outPut.println("<td class=\"noBborderStyle\" nowrap align=\"left\"> <input type=\"image\" name= \"\"  src=\"" + GlobalConstants.ICONPATH + GlobalConstants.EDITICON + "\" border=\"0\" ");
                        outPut.println(" onclick=\"" + workingChangeFunctionStr + "\";></td>");
                    } else if (workingChangeFunctionStr.length() > 0
                            && editable
                            && workingDeleteFunctionStr.length() == 0) {
                        outPut.println("<td class=\"noBborderStyle\" nowrap align=\"left\"> <input type=\"image\" name= \"\"  src=\"" + GlobalConstants.ICONPATH + GlobalConstants.PARENTICON + "\" border=\"0\" ");
                        outPut.println(" onclick=\"" + workingChangeFunctionStr + "\";></td>");
                    } else {
                        outPut.println("<td class=\"noBborderStyle\" nowrap align=\"left\"> <img id=\"img1\" height=\"12px\" width=\"12px\" src=\"" + GlobalConstants.ICONPATH + GlobalConstants.NOTALLOWED + "\" border=\"0\" </td>");
                    }
                    if (workingDeleteFunctionStr.length() > 0 && editable) {
                        outPut.println("<td class=\"noBborderStyle\" nowrap align=\"left\"> <input type=\"image\" name= \"\"  src=\"" + GlobalConstants.ICONPATH + GlobalConstants.DELETEICON + "\" border=\"0\" ");
                        outPut.println(" onclick=\"" + workingDeleteFunctionStr + "\";></td>");
                    }
                    outPut.println("\n</tr></TABLE>\n");
                    outPut.println("<span id=\"span" + name + "\" class=\"clsHide\">\n");
                    generateTreeRecursivly(suborder.getId(), thisLevel, outPut, editable, filteredHierarchy);
                    outPut.println("</span>");
                } catch (IOException ioe) {
                    LOG.error("Error in Tree Tag!");
                }
            }
        }
    }

    /**
     * Methode which ends the tag
     *
     * @param out
     * @return
     */
    public int doEndTag(JspWriter out) {
        try {
            out.print("");
        } catch (IOException ioe) {
            System.out.println("Error in TreeTag: " + ioe);
        }
        return (EVAL_PAGE);
    }

    /**
     * Methode which ends the tag, using the one above
     */
    public int doEndTag() {
        JspWriter out = pageContext.getOut();
        return doEndTag(out);
    }

    /**
     * Outputs the correct Javascript to generate dynamic tree views of listings.
     * The correct code depends on the browser used by the client.
     */
    private void printScript(JspWriter out) throws IOException {
        out.println("<script language='JavaScript'>");
        out.println("function nodeClick( evt, eSrc, id, open, closed ) { ");
        // Netscape and Mozilla specific Javascript
        if ((browser != null) && (browser.indexOf("MSIE") < 0)) {
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
        out.println("eImg.src = ('" + GlobalConstants.ICONPATH + "' + open);");
        out.println("} else {");
        out.println("eImg.src = ('" + GlobalConstants.ICONPATH + "' + closed);");
        out.println("}");
        out.println("} </script>");
        out.println("<style type='text/css'>");
        out.println("   .clsShow { }");
        out.println("   .clsHide { display: none; }");
        out.println("</style>");
    }

}


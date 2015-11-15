/*
 * {{{ header & license
 * Copyright (c) 2007 Wisconsin Court System
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
package org.xhtmlrenderer.newtable;

import java.awt.Rectangle;
import java.util.List;
import java.util.Set;

import org.xhtmlrenderer.css.constants.CSSName;
import org.xhtmlrenderer.css.constants.IdentValue;
import org.xhtmlrenderer.css.style.CalculatedStyle;
import org.xhtmlrenderer.css.style.CssContext;
import org.xhtmlrenderer.css.style.Length;
import org.xhtmlrenderer.css.style.derived.BorderPropertySet;
import org.xhtmlrenderer.css.style.derived.RectPropertySet;
import org.xhtmlrenderer.layout.CollapsedBorderSide;
import org.xhtmlrenderer.layout.FloatManager;
import org.xhtmlrenderer.layout.LayoutContext;
import org.xhtmlrenderer.render.BlockBox;
import org.xhtmlrenderer.render.BorderPainter;
import org.xhtmlrenderer.render.Box;
import org.xhtmlrenderer.render.ContentLimit;
import org.xhtmlrenderer.render.ContentLimitContainer;
import org.xhtmlrenderer.render.PageBox;
import org.xhtmlrenderer.render.RenderingContext;

public class TableCellBox extends BlockBox {
    public static final TableCellBox SPANNING_CELL = new TableCellBox();
    
    private int _row;
    private int _col;
    
    private TableBox _table;
    private TableSectionBox _section;
    
    private BorderPropertySet _collapsedLayoutBorder;
    private BorderPropertySet _collapsedPaintingBorder;
    
    private CollapsedBorderValue _collapsedBorderTop;
    private CollapsedBorderValue _collapsedBorderRight;
    private CollapsedBorderValue _collapsedBorderBottom;
    private CollapsedBorderValue _collapsedBorderLeft;
    
    // 'double', 'solid', 'dashed', 'dotted', 'ridge', 'outset', 'groove', and the lowest: 'inset'. 
    private static final int[] BORDER_PRIORITIES = new int[IdentValue.getIdentCount()];
    
    static {
        BORDER_PRIORITIES[IdentValue.DOUBLE.FS_ID] = 1;
        BORDER_PRIORITIES[IdentValue.SOLID.FS_ID] = 2;
        BORDER_PRIORITIES[IdentValue.DASHED.FS_ID] = 3;
        BORDER_PRIORITIES[IdentValue.DOTTED.FS_ID] = 4;
        BORDER_PRIORITIES[IdentValue.RIDGE.FS_ID] = 5;
        BORDER_PRIORITIES[IdentValue.OUTSET.FS_ID] = 6;
        BORDER_PRIORITIES[IdentValue.GROOVE.FS_ID] = 7;
        BORDER_PRIORITIES[IdentValue.INSET.FS_ID] = 8;
    }
    
    private static final int BCELL = 10;
    private static final int BROW = 9;
    private static final int BROWGROUP = 8;
    private static final int BCOL = 7;
    private static final int BTABLE = 6;

    private enum BorderSide {
        TOP, RIGHT, BOTTOM, LEFT
    }
    
    public TableCellBox() {
    }
    
    public BlockBox copyOf() {
        TableCellBox result = new TableCellBox();
        result.setStyle(getStyle());
        result.setElement(getElement());
        
        return result;
    }
    
    public BorderPropertySet getBorder(CssContext cssCtx) {
        if (getTable().getStyle().isCollapseBorders()) {
            // Should always be non-null, but might not be if layout code crashed
            return _collapsedLayoutBorder == null ? 
                    BorderPropertySet.EMPTY_BORDER : _collapsedLayoutBorder;
        } else {
            return super.getBorder(cssCtx);
        }
    }
    
    public void calcCollapsedBorder(CssContext c) {
        BorderPropertySet top = collapsedBorder(c, BorderSide.TOP);
        BorderPropertySet right = collapsedBorder(c, BorderSide.RIGHT);
        BorderPropertySet bottom = collapsedBorder(c, BorderSide.BOTTOM);
        BorderPropertySet left = collapsedBorder(c, BorderSide.LEFT);
        
        _collapsedPaintingBorder = new BorderPropertySet(top, right, bottom, left);
        
        _collapsedLayoutBorder = new BorderPropertySet(top, right, bottom, left);
        
        _collapsedBorderTop = new CollapsedBorderValue(top.topStyle(), (int) top.top(), top.topColor(), 1);
        _collapsedBorderRight = new CollapsedBorderValue(right.rightStyle(), (int) right.right(), right.rightColor(), 1);
        _collapsedBorderBottom = new CollapsedBorderValue(bottom.bottomStyle(), (int) bottom.bottom(), bottom.topColor(), 1);
        _collapsedBorderLeft = new CollapsedBorderValue(left.leftStyle(), (int) left.left(), left.topColor(), 1);
    }

    public int getCol() {
        return _col;
    }

    public void setCol(int col) {
        _col = col;
    }

    public int getRow() {
        return _row;
    }

    public void setRow(int row) {
        _row = row;
    }
    
    public void layout(LayoutContext c) {
        super.layout(c);
    }
    
    public TableBox getTable() {
        // cell -> row -> section -> table
        if (_table == null) {
            _table = (TableBox)getParent().getParent().getParent();
        }
        return _table;
    }
    
    protected TableSectionBox getSection() {
        if (_section == null) {
            _section = (TableSectionBox)getParent().getParent();
        }
        return _section;
    }
    
    public Length getOuterStyleWidth(CssContext c) {
        Length result = getStyle().asLength(c, CSSName.WIDTH);
        if (result.isVariable() || result.isPercent()) {
            return result;
        }
        
        int bordersAndPadding = 0;
        BorderPropertySet border = getBorder(c);
        bordersAndPadding += (int)border.left() + (int)border.right();
        
        RectPropertySet padding = getPadding(c);
        bordersAndPadding += (int)padding.left() + (int)padding.right();
        
        result.setValue(result.value() + bordersAndPadding);
        
        return result;
    }
    
    public Length getOuterStyleOrColWidth(CssContext c) {
        Length result = getOuterStyleWidth(c);
        if (getStyle().getColSpan() > 1 || ! result.isVariable()) {
            return result;
        }
        TableColumn col = getTable().colElement(getCol());
        if (col != null) {
            // XXX Need to add in collapsed borders from cell (if collapsing borders)
            result = col.getStyle().asLength(c, CSSName.WIDTH);
        }
        return result;
    }
    
    public void setLayoutWidth(LayoutContext c, int width) {
        calcDimensions(c);
        
        setContentWidth(width - getLeftMBP() - getRightMBP());
    }
    
    public boolean isAutoHeight() {
        return getStyle().isAutoHeight() || ! getStyle().hasAbsoluteUnit(CSSName.HEIGHT);
    }
    
    public int calcBaseline(LayoutContext c) {
        int result = super.calcBaseline(c);
        if (result != NO_BASELINE) {
            return result;
        } else {
            Rectangle contentArea = getContentAreaEdge(getAbsX(), getAbsY(), c);
            return (int)contentArea.getY();
        }
    }
    
    public int calcBlockBaseline(LayoutContext c) {
        return super.calcBaseline(c);
    }
    
    public void moveContent(LayoutContext c, final int deltaY) {
        for (int i = 0; i < getChildCount(); i++) {
            Box b = getChild(i);
            b.setY(b.getY() + deltaY);
        }
        
        getPersistentBFC().getFloatManager().performFloatOperation(
                new FloatManager.FloatOperation() {
                    public void operate(Box floater) {
                        floater.setY(floater.getY() + deltaY);
                    }
                });
        
        calcChildLocations();
    }
    
    public boolean isPageBreaksChange(LayoutContext c, int posDeltaY) {
        if (! c.isPageBreaksAllowed()) {
            return false;
        }
        
        PageBox page = c.getRootLayer().getFirstPage(c, this);
        
        int bottomEdge = getAbsY() + getChildrenHeight();
        
        return page != null && (bottomEdge >= page.getBottom() - c.getExtraSpaceBottom() ||
                    bottomEdge + posDeltaY >= page.getBottom() - c.getExtraSpaceBottom());
    }
    
    public IdentValue getVerticalAlign() {
        IdentValue val = getStyle().getIdent(CSSName.VERTICAL_ALIGN);
        
        if (val == IdentValue.TOP || val == IdentValue.MIDDLE || val == IdentValue.BOTTOM) {
            return val;
        } else {
            return IdentValue.BASELINE;
        }
    }
    
    private boolean isPaintBackgroundsAndBorders() {
        boolean showEmpty = getStyle().isShowEmptyCells();
        // XXX Not quite right, but good enough for now 
        // (e.g. absolute boxes will be counted as content here when the spec 
        // says the cell should be treated as empty).  
        return showEmpty || getChildrenContentType() != BlockBox.CONTENT_EMPTY;
                    
    }
    
    public void paintBackground(RenderingContext c) {
        if (isPaintBackgroundsAndBorders() && getStyle().isVisible()) {
            Rectangle bounds;
            if (c.isPrint() && getTable().getStyle().isPaginateTable()) {
                bounds = getContentLimitedBorderEdge(c);
            } else {
                bounds = getPaintingBorderEdge(c);    
            }
            
            if (bounds != null) {
                paintBackgroundStack(c, bounds);
            }
        }
    }

    private void paintBackgroundStack(RenderingContext c, Rectangle bounds) {
        Rectangle imageContainer;
        
        BorderPropertySet border = getStyle().getBorder(c);
        TableColumn column = getTable().colElement(getCol());
        if (column != null) {
            c.getOutputDevice().paintBackground(
                    c, column.getStyle(), 
                    bounds, getTable().getColumnBounds(c, getCol()),
                    border);
        }
        
        Box row = getParent();
        Box section = row.getParent();
        
        CalculatedStyle tableStyle = getTable().getStyle();
        
        CalculatedStyle sectionStyle = section.getStyle();
        
        imageContainer = section.getPaintingBorderEdge(c);
        imageContainer.y += tableStyle.getBorderVSpacing(c);
        imageContainer.height -= tableStyle.getBorderVSpacing(c);
        imageContainer.x += tableStyle.getBorderHSpacing(c);
        imageContainer.width -= 2*tableStyle.getBorderHSpacing(c);
        
        c.getOutputDevice().paintBackground(c, sectionStyle, bounds, imageContainer, border);
        
        CalculatedStyle rowStyle = row.getStyle();
        
        imageContainer = row.getPaintingBorderEdge(c);
        imageContainer.x += tableStyle.getBorderHSpacing(c);
        imageContainer.width -= 2*tableStyle.getBorderHSpacing(c);
        
        c.getOutputDevice().paintBackground(c, rowStyle, bounds, imageContainer, border);
        
        c.getOutputDevice().paintBackground(c, getStyle(), bounds, getPaintingBorderEdge(c), border);
    }
    
    public void paintBorder(RenderingContext c) {
        if (isPaintBackgroundsAndBorders() && ! hasCollapsedPaintingBorder()) {
            // Collapsed table borders are painted separately
            if (c.isPrint() && getTable().getStyle().isPaginateTable() && getStyle().isVisible()) {
                Rectangle bounds = getContentLimitedBorderEdge(c);
                if (bounds != null) {
                    c.getOutputDevice().paintBorder(c, getStyle(), bounds, getBorderSides());
                }
            } else {
                super.paintBorder(c);
            }
        }
    }
    
    public void paintCollapsedBorder(RenderingContext c, int side) {
        c.getOutputDevice().paintCollapsedBorder(
                c, getCollapsedPaintingBorder(), getCollapsedBorderBounds(c), side);
    }
    
    private Rectangle getContentLimitedBorderEdge(RenderingContext c) {
        Rectangle result = getPaintingBorderEdge(c);
        
        TableSectionBox section = getSection();
        if (section.isHeader() || section.isFooter()) {
            return result;
        }
        
        ContentLimitContainer contentLimitContainer = ((TableRowBox)getParent()).getContentLimitContainer();
        ContentLimit limit = contentLimitContainer.getContentLimit(c.getPageNo());
        
        if (limit == null) {
            return null;
        } else {
            if (limit.getTop() == ContentLimit.UNDEFINED || 
                    limit.getBottom() == ContentLimit.UNDEFINED) {
                return result;
            }

            int top;
            if (c.getPageNo() == contentLimitContainer.getInitialPageNo()) {
                top = result.y;
            } else {
                top = limit.getTop() - ((TableRowBox)getParent()).getExtraSpaceTop() ;
            }
            
            int bottom;
            if (c.getPageNo() == contentLimitContainer.getLastPageNo()) {
                bottom = result.y + result.height;
            } else {
                bottom = limit.getBottom() + ((TableRowBox)getParent()).getExtraSpaceBottom(); 
            }
            
            result.y = top;
            result.height = bottom - top;
            
            return result;
        }
    }  
    
    public Rectangle getChildrenClipEdge(RenderingContext c) {
        if (c.isPrint() && getTable().getStyle().isPaginateTable()) {
            Rectangle bounds = getContentLimitedBorderEdge(c);
            if (bounds != null) {
                BorderPropertySet border = getBorder(c);
                RectPropertySet padding = getPadding(c);
                bounds.y += (int)border.top() + (int)padding.top();
                bounds.height -= (int)border.height() + (int)padding.height();
                return bounds;
            }
        } 
        
        return super.getChildrenClipEdge(c);
    }
    
    protected boolean isFixedWidthAdvisoryOnly() {
        return getTable().getStyle().isIdent(CSSName.TABLE_LAYOUT, IdentValue.AUTO);
    }
    
    protected boolean isSkipWhenCollapsingMargins() {
        return true;
    } 

    private static BorderSide flipSide(BorderSide side) {
        switch (side) {
            case TOP:
                return BorderSide.BOTTOM;
            case RIGHT:
                return BorderSide.LEFT;
            case BOTTOM:
                return BorderSide.TOP;
            case LEFT:
                return BorderSide.RIGHT;
        }
    
        throw new IllegalArgumentException("Unexpected side: " + side);
    }
    
    private static IdentValue getBorderStyle(BorderPropertySet border, BorderSide side) {
        switch (side) {
            case TOP:
                return border.topStyle();
            case RIGHT:
                return border.rightStyle();
            case BOTTOM:
                return border.bottomStyle();
            case LEFT:
                return border.leftStyle();
        }

        throw new IllegalArgumentException("Unexpected side: " + side);
    }
    
    private static float getBorderWidth(BorderPropertySet border, BorderSide side) {
        switch (side) {
            case TOP:
                return border.top();
            case RIGHT:
                return border.right();
            case BOTTOM:
                return border.bottom();
            case LEFT:
                return border.left();
        }

        throw new IllegalArgumentException("Unexpected side: " + side);
    }

    private static BorderPropertySet nullSafeBorderLookup(CssContext c, Box box) {
        if (box != null && box.getStyle().getBorder(c) != null) {
            return box.getStyle().getBorder(c);
        } else {
            return BorderPropertySet.EMPTY_BORDER;
        }
    }

    private static boolean useOtherBorder(
            BorderPropertySet currentBorder,
            BorderSide currentSide,
            BorderPropertySet otherBorder,
            BorderSide otherSide) {
        if (IdentValue.HIDDEN.equals(getBorderStyle(otherBorder, otherSide))) {
            return true;
        }

        if (getBorderWidth(otherBorder, otherSide) > getBorderWidth(currentBorder, currentSide)) {
            return true;
        }

        return BORDER_PRIORITIES[getBorderStyle(otherBorder, otherSide).FS_ID] > 
            BORDER_PRIORITIES[getBorderStyle(currentBorder, currentSide).FS_ID];
    }

    private BorderPropertySet collapsedBorder(CssContext c, BorderSide side) {
        TableCellBox touchingCell;

        switch (side) {
            case TOP:
                touchingCell = getTable().cellAbove(this);
                break;
            case RIGHT:
                touchingCell = getTable().cellRight(this);
                break;
            case BOTTOM:
                touchingCell = getTable().cellBelow(this);
                break;
            case LEFT:
                touchingCell = getTable().cellLeft(this);
                break;
            default:
                throw new IllegalArgumentException("Unexpected side: " + side);
        }

        BorderPropertySet currentBorder = nullSafeBorderLookup(c, this);
        BorderPropertySet touchingBorder = nullSafeBorderLookup(c, touchingCell);

        // look for any uses of HIDDEN
        if (IdentValue.HIDDEN.equals(getBorderStyle(currentBorder, side))) {
            return currentBorder;
        }
        
        if (IdentValue.HIDDEN.equals(getBorderStyle(touchingBorder, flipSide(side)))) {
            return BorderPropertySet.EMPTY_BORDER;
        }

        // TODO: check row group, column, column group, table

        // look for first non-NONE borders and compare
        boolean currentIsNone = IdentValue.NONE.equals(getBorderStyle(currentBorder, side));
        boolean touchingIsNone = IdentValue.NONE.equals(getBorderStyle(touchingBorder, flipSide(side)));

        if (currentIsNone || touchingIsNone) {
            return currentBorder;
        }

        if (useOtherBorder(currentBorder, side, touchingBorder, flipSide(side))) {
            return BorderPropertySet.EMPTY_BORDER;
        }

        // the spec does not define who wins with all else being equal,
        // in Chrome the right border wins and the bottom border wins
        // so we'll go with that strategy as well
        if (BorderSide.LEFT.equals(side) || BorderSide.TOP.equals(side)) {
            return BorderPropertySet.EMPTY_BORDER;
        } else {
            return currentBorder;
        }
    }


    
    private Rectangle getCollapsedBorderBounds(CssContext c) {
        BorderPropertySet border = getCollapsedPaintingBorder();
        Rectangle bounds = getPaintingBorderEdge(c);
        bounds.x -= (int) border.left() / 2;
        bounds.y -= (int) border.top() / 2;
        bounds.width += (int) border.left() / 2 + ((int) border.right() + 1) / 2;
        bounds.height += (int) border.top() / 2 + ((int) border.bottom() + 1) / 2;
        
        return bounds;
    }
    
    public Rectangle getPaintingClipEdge(CssContext c) {
        if (hasCollapsedPaintingBorder()) {
            return getCollapsedBorderBounds(c);
        } else {
            return super.getPaintingClipEdge(c);
        }
    }
    
    public boolean hasCollapsedPaintingBorder() {
        return _collapsedPaintingBorder != null;
    }
    
    protected BorderPropertySet getCollapsedPaintingBorder() {
        return _collapsedPaintingBorder;
    }

    public CollapsedBorderValue getCollapsedBorderBottom() {
        return _collapsedBorderBottom;
    }

    public CollapsedBorderValue getCollapsedBorderLeft() {
        return _collapsedBorderLeft;
    }

    public CollapsedBorderValue getCollapsedBorderRight() {
        return _collapsedBorderRight;
    }

    public CollapsedBorderValue getCollapsedBorderTop() {
        return _collapsedBorderTop;
    }
    
    public void addCollapsedBorders(Set all, List borders) {
        if (_collapsedBorderTop.exists() && !all.contains(_collapsedBorderTop)) {
            all.add(_collapsedBorderTop);
            borders.add(new CollapsedBorderSide(this, BorderPainter.TOP));
        }
        
        if (_collapsedBorderRight.exists() && !all.contains(_collapsedBorderRight)) {
            all.add(_collapsedBorderRight);
            borders.add(new CollapsedBorderSide(this, BorderPainter.RIGHT));
        }
        
        if (_collapsedBorderBottom.exists() && !all.contains(_collapsedBorderBottom)) {
            all.add(_collapsedBorderBottom);
            borders.add(new CollapsedBorderSide(this, BorderPainter.BOTTOM));
        }
        
        if (_collapsedBorderLeft.exists() && !all.contains(_collapsedBorderLeft)) {
            all.add(_collapsedBorderLeft);
            borders.add(new CollapsedBorderSide(this, BorderPainter.LEFT));
        }
    }
    
    // Treat height as if it specifies border height (i.e. 
    // box-sizing: border-box in CSS3).  There doesn't seem to be any
    // justification in the spec for this, but everybody does it 
    // (in standards mode) so I guess we will too
    protected int getCSSHeight(CssContext c) {
        if (getStyle().isAutoHeight()) {
            return -1;
        } else {
            int result = (int)getStyle().getFloatPropertyProportionalWidth(
                    CSSName.HEIGHT, getContainingBlock().getContentWidth(), c);
            
            BorderPropertySet border = getBorder(c);
            result -= (int)border.top() + (int)border.bottom();
            
            RectPropertySet padding = getPadding(c);
            result -= (int)padding.top() + (int)padding.bottom();
            
            return result >= 0 ? result : -1;
        }
    }
    
    protected boolean isAllowHeightToShrink() {
        return false;
    } 
    
    public boolean isNeedsClipOnPaint(RenderingContext c) {
        boolean result = super.isNeedsClipOnPaint(c);
        if (result) {
            return result;
        }
        
        return c.isPrint() && getTable().getStyle().isPaginateTable() &&
                ((TableRowBox)getParent()).getContentLimitContainer().isContainsMultiplePages();
    }
}

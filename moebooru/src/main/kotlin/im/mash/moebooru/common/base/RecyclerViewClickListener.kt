/*
 * Copyright (C) 2019 by onlymash <im@fiepi.me>, All rights reserved
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package im.mash.moebooru.common.base

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

open class RecyclerViewClickListener(context: Context, private val listener: OnItemClickListener) : RecyclerView.OnItemTouchListener {

    //手势检测类
    private val gestureDetector: GestureDetector

    private var childView: View? = null

    private var position = -1

    //内部接口，定义点击方法以及长按方法
    interface OnItemClickListener {

        fun onItemClick(itemView: View, position: Int)

        fun onItemLongClick(itemView: View, position: Int)

    }

    init {
        gestureDetector = GestureDetector(context,
                object : GestureDetector.SimpleOnGestureListener() { //这里选择SimpleOnGestureListener实现类，可以根据需要选择重写的方法
                    //单击事件
                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        if (childView != null) {
                            listener.onItemClick(childView!!, position)
                            return true
                        }
                        return false
                    }

                    //长按事件
                    override fun onLongPress(e: MotionEvent) {
                        if (childView != null) {
                            listener.onItemLongClick(childView!!, position)
                            //长按振动
                            childView!!.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    }
                })
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        childView = rv.findChildViewUnder(e.x, e.y)
        if (childView != null) {
            position = rv.getChildLayoutPosition(childView!!)
        }
        //把事件交给 GestureDetector 处理
        return gestureDetector.onTouchEvent(e)
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
}

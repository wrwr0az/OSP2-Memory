package osp.Memory;

import java.util.*;
import osp.IFLModules.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.Interrupts.*;

/**
 * The MMU class contains the student code that performs the work of handling a
 * memory reference. It is responsible for calling the interrupt handler if a
 * page fault is required.
 * 
 * @OSPProject Memory
 */
public class MMU extends IflMMU {
	/**
	 * This method is called once before the simulation starts. Can be used to
	 * initialize the frame table and other static variables.
	 * 
	 * @OSPProject Memory
	 */

	public static int Cursor;
	public static int wantFree;

	public static void init() {
		// your code goes here

		for (int i = 0; i < MMU.getFrameTableSize(); i++)
			MMU.setFrame(i, new FrameTableEntry(i));

		Cursor = 0;
		wantFree = 1;

	}

	/**
	 * This method handlies memory references. The method must calculate, which
	 * memory page contains the memoryAddress, determine, whether the page is valid,
	 * start page fault by making an interrupt if the page is invalid, finally, if
	 * the page is still valid, i.e., not swapped out by another thread while this
	 * thread was suspended, set its frame as referenced and then set it as dirty if
	 * necessary. (After pagefault, the thread will be placed on the ready queue,
	 * and it is possible that some other thread will take away the frame.)
	 * 
	 * @param memoryAddress A virtual memory address
	 * @param referenceType The type of memory reference to perform
	 * @param thread        that does the memory access (e.g., MemoryRead or
	 *                      MemoryWrite).
	 * @return The referenced page.
	 * 
	 * @OSPProject Memory
	 */
	static public PageTableEntry do_refer(int memoryAddress, int referenceType, ThreadCB thread) {
		// your code goes here
		// compute page to which memory belongs
		int pageAddress = (int) (memoryAddress / Math.pow(2, getVirtualAddressBits() - getPageAddressBits()));
		PageTableEntry page = getPTBR().pages[pageAddress];
		FrameTableEntry pageFrame = page.getFrame();
		// check if the page is invalid
		if (!page.isValid()) {
			// check is validation thread of the page is null
			if (page.getValidatingThread() == null) {
				// do interrupt
				InterruptVector.setReferenceType(referenceType);
				InterruptVector.setPage(page);
				InterruptVector.setThread(thread);
				CPU.interrupt(PageFault);
			}
			else {
				// suspend the thread
				thread.suspend(page);
				// if thread not kill
				if (thread.getStatus() != ThreadKill) {
					// set referenced
					pageFrame.setReferenced(true);
					// if reference is memory write
					if (referenceType == MemoryWrite) {
						// set dirty
						page.getFrame().setDirty(true);
					}
				} 
			}
		}
		else {
			if (thread.getStatus() != ThreadKill) { ///////////////////// !!!!!!!! I don't know if this condition is necessary or not
				// set referenced
				pageFrame.setReferenced(true);
				if (referenceType == MemoryWrite) {
					// set dirty
					page.getFrame().setDirty(true);
				}
			}
		}
		// return page
		return page;
	}

	/**
	 * Called by OSP after printing an error message. The student can insert code
	 * here to print various tables and data structures in their state just after
	 * the error happened. The body can be left empty, if this feature is not used.
	 * 
	 * @OSPProject Memory
	 */
	public static void atError() {
		// your code goes here (if needed)

	}

	/**
	 * Called by OSP after printing a warning message. The student can insert code
	 * here to print various tables and data structures in their state just after
	 * the warning happened. The body can be left empty, if this feature is not
	 * used.
	 * 
	 * @OSPProject Memory
	 */
	public static void atWarning() {
		// your code goes here (if needed)

	}

	/*
	 * Feel free to add methods/fields to improve the readability of your code
	 */

}

/*
 * Feel free to add local classes to improve the readability of your code
 */

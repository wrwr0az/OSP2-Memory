package osp.Memory;

import java.util.*;
import osp.IFLModules.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.Interrupts.*;

/**
 * Purpose: The MMU class contains code that performs the work of handling a
 * memory reference. It is responsible for calling the interrupt handler if a
 * page fault is required.
 * 
 * @OSPProject Memory Authors: Abdulaziz Hasan 1555528, Mohammed Shukri 1647376
 *             Date of the Last modification: 16/4/2020
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
		Cursor = 0;
		wantFree = 1;

		for (int i = 0; i < MMU.getFrameTableSize(); i++)
			setFrame(i, new FrameTableEntry(i));

	}

	/**
	 * Purpose: This method handlies memory references. It will calculate which
	 * memory page contains the memoryAddress and determine whether the page is
	 * valid,. Furthermore, it'll start page fault by making an interrupt if the
	 * page is invalid, finally, if the page is still valid, i.e., not swapped out
	 * by another thread while this thread was suspended, it will set its frame as
	 * referenced and then set it as dirty if necessary. (After pagefault, the
	 * thread will be placed on the ready queue, and it is possible that some other
	 * thread will take away the frame.)
	 * 
	 * Inputs: - memoryAddress A virtual memory address - referenceType The type of
	 * memory reference to perform - thread that does the memory access (e.g.,
	 * MemoryRead or MemoryWrite).
	 * 
	 * Output: The referenced page.
	 * 
	 * @OSPProject Memory Authors: Abdulaziz Hasan 1555528, Mohammed Shukri 1647376
	 *             Date of the Last modification: 16/4/2020
	 */

	static public PageTableEntry do_refer(int memoryAddress, int referenceType, ThreadCB thread) {
		// Compute the page address
		int pageAddress = memoryAddress / ((int) Math.pow(2, getVirtualAddressBits() - getPageAddressBits()));
		PageTableEntry page = MMU.getPTBR().pages[pageAddress];

		if (page.isValid()) {
			
			page.getFrame().setReferenced(true);
			if (referenceType == MemoryWrite) {
				page.getFrame().setDirty(true);

			}

			// return page;
		}
		// Check if the page is invalid
		else {

			// Check if validation thread of the page is null
			if (page.getValidatingThread() == null) {

				InterruptVector.setReferenceType(referenceType);
				InterruptVector.setPage(page);
				InterruptVector.setThread(thread);
				CPU.interrupt(PageFault);
				// due to warning 1:
				ThreadCB.dispatch();

				if (thread.getStatus() != ThreadKill) {

					// Set the page's frame as referenced.
					page.getFrame().setReferenced(true);

					// Set the frame dirty bit to true (dirty) if the reference type is
					// "MemoryWrite".
					if (referenceType == MemoryWrite) {
						page.getFrame().setDirty(true);
					}
				}
			}

			else {

				// Suspend the thread
				thread.suspend(page);

				

				if (thread.getStatus() != ThreadKill) {

					// Set the page's frame as referenced.
					page.getFrame().setReferenced(true);

					// Set the frame dirty bit to true (dirty) if the reference type is
					// "MemoryWrite".
					if (referenceType == MemoryWrite) {
						page.getFrame().setDirty(true);
					}
				}
			}

		}

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

	}

}
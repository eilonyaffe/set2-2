package bguspl.set.ex;

import java.util.LinkedList;

public class ThreadSafeLinkedList {
    protected LinkedList<LinkPlayerSet> list;

    public ThreadSafeLinkedList(){
        this.list = new LinkedList<LinkPlayerSet>();
    }


    public synchronized boolean add(LinkPlayerSet newLink) {
        System.out.println("added set by player: "+newLink.player.id);
        return this.list.add(newLink);
    }

    public synchronized LinkPlayerSet removeFirst() {
        return this.list.removeFirst();
    }

    public synchronized boolean remove(LinkPlayerSet removedLink) {
        if (this.list.isEmpty() || !this.list.contains(removedLink)) return false;
        return this.list.remove(removedLink);
    }

    public synchronized boolean removeAll() { //removes all players from this slot, removes their tokens from ui, and return their ids
        if (list == null) return true;
        this.list.clear();
        return true;
    }

    public synchronized boolean isEmpty() {
        return (list.peekFirst()==null);
    }
}

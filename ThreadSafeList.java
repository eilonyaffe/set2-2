package bguspl.set.ex;

import java.util.ArrayList;

import bguspl.set.Env;

public class ThreadSafeList {

    protected ArrayList<Integer> list = new ArrayList<>();
    private final Env env;
    private int slot;

    public ThreadSafeList(Env _env, int _slot){
        this.env = _env;
        this.slot = _slot;
    }


    public synchronized boolean add(int value) {
        if (!list.contains(value)) { 
            list.add(value);
            env.ui.placeToken(value, this.slot);
            return true;
        }
        return false;
    }

    public synchronized boolean remove(int value) {
        if (list == null) return false;
        boolean exists = list.remove(Integer.valueOf(value));
        if(exists){
            env.ui.removeToken(value, this.slot);
        }
        return exists;
    }

    public synchronized int[] removeAll() { //removes all players from this slot, removes their tokens from ui, and return their ids
        if (list == null) return null;

        int[] players = new int[this.list.size()];
        for(int i=0;i<players.length;i++){
            players[i] = this.list.get(i).intValue();
            env.ui.removeToken(players[i], this.slot);
        }
        this.list.clear();
        return players;
    }

    public synchronized boolean contains(int value) {
        if (list == null) return false;
        return list.contains(value);
    }

    public synchronized int size() {
        if (list == null) return -1;
        return list.size();
    }

    public synchronized int[] getPlayers() { 
        int[] players = new int[this.list.size()];
        for(int i=0;i<players.length;i++){
            players[i] = this.list.get(i).intValue();
        }
        return players;
    }

    //EYTODO delete later, used for testing
    public void print() {
        for(int i=0;i<this.list.size();i++){
            System.out.println(this.list.get(i)); 
        }
    }
}


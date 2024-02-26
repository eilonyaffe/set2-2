package bguspl.set.ex;

public class LinkPlayerSet {
    public int[] cards;
    protected Player player;
    // protected Thread playerThread;


    public LinkPlayerSet(int[] _cards, Player _player){
        this.cards=_cards; 
        this.player=_player;
    }

    public boolean containsCards(int[] cardsToRemove){
        boolean contains = false;
        for(int i=0;!contains && i<3;i++){
            int card = cardsToRemove[i];
            for(int cardInLink: this.cards){
                if(cardInLink==card){
                    contains = true;
                    break;
                }
            }
        }
        return contains;
    }
}

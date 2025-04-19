package wraith.fwaystones.util;

public class NumismaticUtils {
    public static CoinsTuple convertCostToCoins(int cost) {
        return new CoinsTuple(
            cost / 10000,
            (cost % 10000) / 100,
            cost % 100
        );
    }

    public static class CoinsTuple {
        public int goldCoins;
        public int silverCoins;
        public int bronzeCoins;

        public CoinsTuple(int goldCoins, int silverCoins, int bronzeCoins) {
            this.bronzeCoins = bronzeCoins;
            this.silverCoins = silverCoins;
            this.goldCoins = goldCoins;
        }
    }
}

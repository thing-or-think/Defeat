package bot.logic;

import jsclub.codefest.sdk.model.support_items.SupportItem;
import jsclub.codefest.sdk.model.npcs.Ally;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;
import jsclub.codefest.sdk.algorithm.PathUtils;

import java.util.Comparator;
import java.util.List;

import sdk.Hero;
import bot.BotContext;
import bot.navigation.PathPlanner;
import bot.memory.BotMemory;

public class CombatManager {
    Hero hero;
    private static final int HEALTH_THRESHOLD_TO_HEAL = 40;
    private static final String ALLY_NPC_ID = "SPIRIT";

    public CombatManager(Hero hero) {
        this.hero = hero;
    }

    public boolean attackEnemy(int maxDistance) throws Exception {
        // Tìm kiếm kẻ địch gần nhất trong phạm vi maxDistance
        Player enemy = EnemySelector.findEnemyInAttackRange();
        if (handleRangedAttack(enemy)) {
            // Nếu có kẻ địch trong phạm vi tấn công, thực hiện tấn công    
            return true;
        }
        enemy = EnemySelector.findEnemyInRange(maxDistance);
        if (handleMeleeAttack(enemy)) {
            // Nếu có kẻ địch trong phạm vi tấn công, thực hiện tấn công
            return true;
        }
        return false;
    }

    public boolean tryHealIfNeeded() throws Exception {
        if (BotContext.player.getHealth() < HEALTH_THRESHOLD_TO_HEAL) {
            // nếu máu dưới ngưỡng, ưu tiên dùng item hỗ trợ hồi máu
            SupportItem bestSupport = findBestSupportItem();
            if (bestSupport != null) {
                System.out.println("Low health. Using support item: " + bestSupport.getId());
                hero.botUseItem(bestSupport.getId());
                return true;
            }

            Ally spiritAlly = findNearestAlly(ALLY_NPC_ID);
            if (spiritAlly != null && PathUtils.checkInsideSafeArea(spiritAlly, BotContext.gameMap.getSafeZone(), BotContext.gameMap.getMapSize())) {
                System.out.println("Low health and no items. Moving to SPIRIT ally for healing.");
                String pathToAlly = PathUtils.getShortestPath(
                        BotContext.gameMap,
                        PathPlanner.getNodesToAvoid(true, false),
                        BotContext.player,
                        spiritAlly,
                        false
                );
                if (pathToAlly != null && !pathToAlly.isEmpty()) {
                    hero.move(pathToAlly);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean handleRangedAttack(Player enemy) throws Exception {
        if (enemy == null) {
            return false; // No enemy to attack
        }
        String path = PathUtils.getShortestPath(BotContext.gameMap, PathPlanner.getNodesToAvoid(false, false), BotContext.player, enemy, false);
        if (path == null) {
            return false;
        }
        
        Weapon rangedWeapons[] = {BotContext.inventory.getGun(), BotContext.inventory.getSpecial(), BotContext.inventory.getThrowable()};
        String types[] = {"shoot", "useSpecial", "throwItem"};
        Weapon bestWeapon = null;
        String previousAction = BotMemory.lastAction.get(BotMemory.lastAction.size() - 1);
        int distance = PathUtils.distance(BotContext.player, enemy);
        boolean check = false;
        for (int i = 0; i < 3; i++) {
            Weapon weapon = rangedWeapons[i];
            check |= types[i].equals(previousAction);
            if (weapon == null || types[i].equals(previousAction)) {
                continue; // Skip if no weapon available
            }
            if ((types[i] != "throwItem" && weapon.getRange()[1] >= distance) || 
                (types[i] == "throwItem" && weapon.getRange()[1] - 1 <= distance && distance <= weapon.getRange()[1] + 1)) {
                if (bestWeapon == null || weapon.getDamage() >= bestWeapon.getDamage()) {
                    bestWeapon = weapon; // Select the weapon with the highest damage within range
                }
            }
            if (bestWeapon != null && enemy.getHealth() <= bestWeapon.getDamage()) {
                break; // If the enemy's health is less than or equal to the weapon's damage, break
            }
        }
        if (bestWeapon == null) {
            return false; // No suitable weapon found
        }
        switch (bestWeapon.getType()) {
            case GUN:
                hero.botShoot(path);
                return true;

            case SPECIAL:
                hero.botUseSpecial(path);
                return true;

            case THROWABLE:
                hero.botThrowItem(path);
                return true;
        }
        if (check) {
            hero.botAttack(path);
            return true;
        }
        return false;
    }

    private boolean handleMeleeAttack(Player enemy) throws Exception {
        if (enemy == null) {
            return false;
        }
        int distance = PathUtils.distance(BotContext.player, enemy);
        String path = PathUtils.getShortestPath(BotContext.gameMap, PathPlanner.getNodesToAvoid(distance > 2, false), BotContext.player, enemy, false);
        if (path == null || path.isEmpty()) {
            return false; // No valid path to the enemy
        }
        String previousAction = BotMemory.lastAction.get(BotMemory.lastAction.size() - 1);
        if (distance == 1) {
            if (previousAction == "attack" && BotContext.inventory.getGun() != null) {
                hero.botShoot(path);
                return true;
            }
            // If the enemy is adjacent, perform a melee attack
            hero.botAttack(path);
            return true; // Successfully attacked the enemy
        } else {
            hero.botMove(path);
            return true;
            // Move towards the enemy if not adjacent
        }
    }

    private SupportItem findBestSupportItem() {
        List<SupportItem> items = hero.getInventory().getListSupportItem();
        if (items == null || items.isEmpty()) return null;

        return hero.getInventory().getListSupportItem().stream()
                .max(Comparator.comparingInt(SupportItem::getHealingHP))
                .orElse(null);
    }

    private Ally findNearestAlly(String allyId) {
        return BotContext.gameMap.getListAllies().stream()
                .filter(ally -> ally.getId().equals(allyId))
                .min(Comparator.comparingInt(ally -> PathUtils.distance(BotContext.player, ally)))
                .orElse(null);
    }

}
/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.game.staticability;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import forge.GameCommand;
import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.card.mana.ManaCostShard;
import forge.game.*;
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardFactoryUtil;
import forge.game.card.CardLists;
import forge.game.card.CardUtil;
import forge.game.cost.Cost;
import forge.game.player.Player;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementHandler;
import forge.game.spellability.AbilityActivated;
import forge.game.spellability.AbilityStatic;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerHandler;
import forge.game.zone.ZoneType;

import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * The Class StaticAbility_Continuous.
 */
public class StaticAbilityContinuous {
	
	public static List<Card> applyContinuousAbility(final StaticAbility stAb) {
        final List<Card> affectedCards = StaticAbilityContinuous.getAffectedCards(stAb);
		return applyContinuousAbility(stAb, affectedCards);
	}

    /**
     * 
     * TODO Write javadoc for this method.
     * 
     * @param stAb
     *            a StaticAbility
     * @return 
     * 
     */
    public static List<Card> applyContinuousAbility(final StaticAbility stAb, List<Card> affectedCards) {
        final Map<String, String> params = stAb.getMapParams();
        final Card hostCard = stAb.getHostCard();
        final Player controller = hostCard.getController();

        final StaticEffect se = new StaticEffect(hostCard);
        final ArrayList<Player> affectedPlayers = StaticAbilityContinuous.getAffectedPlayers(stAb);
        final Game game = hostCard.getGame();

        se.setAffectedCards(affectedCards);
        se.setAffectedPlayers(affectedPlayers);
        se.setParams(params);
        se.setTimestamp(hostCard.getTimestamp());
        game.getStaticEffects().addStaticEffect(se);

        String changeColorWordsTo = null;

        String addP = "";
        int powerBonus = 0;
        String addT = "";
        int toughnessBonus = 0;
        String setP = "";
        int setPower = -1;
        String setT = "";
        int setToughness = -1;
        int keywordMultiplier = 1;

        String[] addKeywords = null;
        String[] addHiddenKeywords = null;
        String[] removeKeywords = null;
        String[] addAbilities = null;
        String[] addReplacements = null;
        String[] addSVars = null;
        String[] addTypes = null;
        String[] removeTypes = null;
        String addColors = null;
        String[] addTriggers = null;
        String[] addStatics = null;
        ArrayList<SpellAbility> addFullAbs = null;
        boolean removeAllAbilities = false;
        boolean removeSuperTypes = false;
        boolean removeCardTypes = false;
        boolean removeSubTypes = false;
        boolean removeCreatureTypes = false;
        boolean controllerMayLookAt = false;

        //Global rules changes
        if (params.containsKey("GlobalRule")) {
            final StaticEffects effects = game.getStaticEffects();
            effects.setGlobalRuleChange(GlobalRuleChange.fromString(params.get("GlobalRule")));
        }

        if (params.containsKey("ChangeColorWordsTo")) {
            changeColorWordsTo = params.get("ChangeColorWordsTo");
        }

        if (params.containsKey("SetPower")) {
            setP = params.get("SetPower");
            setPower = AbilityUtils.calculateAmount(hostCard, setP, stAb);
        }

        if (params.containsKey("SetToughness")) {
            setT = params.get("SetToughness");
            setToughness = AbilityUtils.calculateAmount(hostCard, setT, stAb);
        }

        if (params.containsKey("AddPower")) {
            addP = params.get("AddPower");
            powerBonus = AbilityUtils.calculateAmount(hostCard, addP, stAb);
            if (!StringUtils.isNumeric(addP) && !addP.equals("AffectedX")) {
                se.setXValue(powerBonus);
            }
        }

        if (params.containsKey("AddToughness")) {
            addT = params.get("AddToughness");
            toughnessBonus = AbilityUtils.calculateAmount(hostCard, addT, stAb);
            if (!StringUtils.isNumeric(addT) && !addT.equals("AffectedX")) {
                se.setYValue(toughnessBonus);
            }
        }

        if (params.containsKey("KeywordMultiplier")) {
            String multiplier = params.get("KeywordMultiplier");
            if (multiplier.equals("X")) {
                se.setXValue(AbilityUtils.calculateAmount(hostCard, "X", stAb));
            } else {
                keywordMultiplier = Integer.valueOf(multiplier);
            }
        }

        if (params.containsKey("AddKeyword")) {
            addKeywords = params.get("AddKeyword").split(" & ");
            final Iterable<String> chosencolors = hostCard.getChosenColors();
            for (final String color : chosencolors) {
                for (int w = 0; w < addKeywords.length; w++) {
                    addKeywords[w] = addKeywords[w].replaceAll("ChosenColor", color.substring(0, 1).toUpperCase().concat(color.substring(1, color.length())));
                }
            }
            final String chosenType = hostCard.getChosenType();
            for (int w = 0; w < addKeywords.length; w++) {
                addKeywords[w] = addKeywords[w].replaceAll("ChosenType", chosenType);
            }
            final String chosenName = hostCard.getNamedCard();
            final String hostCardUID = Integer.toString(hostCard.getId()); // Protection with "doesn't remove" effect
            for (int w = 0; w < addKeywords.length; w++) {
                if (addKeywords[w].startsWith("Protection:")) {
                    addKeywords[w] = addKeywords[w].replaceAll("ChosenName", "Card.named" + chosenName).replace("HostCardUID", hostCardUID);
                }
            }
            if (params.containsKey("SharedKeywordsZone")) {
                List<ZoneType> zones = ZoneType.listValueOf(params.get("SharedKeywordsZone"));
                String[] restrictions = params.containsKey("SharedRestrictions") ? params.get("SharedRestrictions").split(",") : new String[] {"Card"};
                List<String> kw = CardFactoryUtil.sharedKeywords(addKeywords, restrictions, zones, hostCard);
                addKeywords = kw.toArray(new String[kw.size()]);
            }
        }

        if (params.containsKey("AddHiddenKeyword")) {
            addHiddenKeywords = params.get("AddHiddenKeyword").split(" & ");
        }

        if (params.containsKey("RemoveKeyword")) {
            removeKeywords = params.get("RemoveKeyword").split(" & ");
        }

        if (params.containsKey("RemoveAllAbilities")) {
            removeAllAbilities = true;
        }

        if (params.containsKey("AddAbility")) {
            final String[] sVars = params.get("AddAbility").split(" & ");
            for (int i = 0; i < sVars.length; i++) {
                sVars[i] = hostCard.getSVar(sVars[i]);
            }
            addAbilities = sVars;
        }

        if (params.containsKey("AddReplacementEffects")) {
            final String[] sVars = params.get("AddReplacementEffects").split(" & ");
            for (int i = 0; i < sVars.length; i++) {
                sVars[i] = hostCard.getSVar(sVars[i]);
            }
            addReplacements = sVars;
        }

        if (params.containsKey("AddSVar")) {
            addSVars = params.get("AddSVar").split(" & ");
        }

        if (params.containsKey("AddType")) {
            addTypes = params.get("AddType").split(" & ");
            if (addTypes[0].equals("ChosenType")) {
                final String chosenType = hostCard.getChosenType();
                addTypes[0] = chosenType;
                se.setChosenType(chosenType);
            } else if (addTypes[0].equals("ImprintedCreatureType")) {
                if (hostCard.hasImprintedCard()) {
                    final Set<String> imprinted = hostCard.getImprintedCards().getFirst().getType().getCreatureTypes();
                    addTypes = imprinted.toArray(new String[imprinted.size()]);
                }
            }
        }

        if (params.containsKey("RemoveType")) {
            removeTypes = params.get("RemoveType").split(" & ");
            if (removeTypes[0].equals("ChosenType")) {
                final String chosenType = hostCard.getChosenType();
                removeTypes[0] = chosenType;
                se.setChosenType(chosenType);
            }
        }

        if (params.containsKey("RemoveSuperTypes")) {
            removeSuperTypes = true;
        }

        if (params.containsKey("RemoveCardTypes")) {
            removeCardTypes = true;
        }

        if (params.containsKey("RemoveSubTypes")) {
            removeSubTypes = true;
        }

        if (params.containsKey("RemoveCreatureTypes")) {
            removeCreatureTypes = true;
        }

        if (params.containsKey("AddColor")) {
            final String colors = params.get("AddColor");
            if (colors.equals("ChosenColor")) {
                addColors = CardUtil.getShortColorsString(hostCard.getChosenColors());
            } else {
                addColors = CardUtil.getShortColorsString(new ArrayList<String>(Arrays.asList(colors.split(
                    " & "))));
            }
        }

        if (params.containsKey("SetColor")) {
            final String colors = params.get("SetColor");
            if (colors.equals("ChosenColor")) {
                addColors = CardUtil.getShortColorsString(hostCard.getChosenColors());
            } else {
                addColors = CardUtil.getShortColorsString(new ArrayList<String>(Arrays.asList(
                        colors.split(" & "))));
            }
            se.setOverwriteColors(true);
        }

        if (params.containsKey("AddTrigger")) {
            final String[] sVars = params.get("AddTrigger").split(" & ");
            for (int i = 0; i < sVars.length; i++) {
                sVars[i] = hostCard.getSVar(sVars[i]);
            }
            addTriggers = sVars;
        }

        if (params.containsKey("AddStaticAbility")) {
            final String[] sVars = params.get("AddStaticAbility").split(" & ");
            for (int i = 0; i < sVars.length; i++) {
                sVars[i] = hostCard.getSVar(sVars[i]);
            }
            addStatics = sVars;
        }

        if (params.containsKey("GainsAbilitiesOf")) {
            final String[] valids = params.get("GainsAbilitiesOf").split(",");
            ArrayList<ZoneType> validZones = new ArrayList<ZoneType>();
            validZones.add(ZoneType.Battlefield);
            if (params.containsKey("GainsAbilitiesOfZones")) {
                validZones.clear();
                for (String s : params.get("GainsAbilitiesOfZones").split(",")) {
                    validZones.add(ZoneType.smartValueOf(s));
                }
            }

            CardCollectionView cardsIGainedAbilitiesFrom = game.getCardsIn(validZones);
            cardsIGainedAbilitiesFrom = CardLists.getValidCards(cardsIGainedAbilitiesFrom, valids, hostCard.getController(), hostCard);

            if (cardsIGainedAbilitiesFrom.size() > 0) {
                addFullAbs = new ArrayList<SpellAbility>();

                for (Card c : cardsIGainedAbilitiesFrom) {
                    for (SpellAbility sa : c.getSpellAbilities()) {
                        if (sa instanceof AbilityActivated) {
                            SpellAbility newSA = ((AbilityActivated) sa).getCopy();
                            newSA.setIntrinsic(false);
                            newSA.setTemporary(true);
                            CardFactoryUtil.correctAbilityChainSourceCard(newSA, hostCard);
                            addFullAbs.add(newSA);
                        }
                    }
                }
            }
        }

        if (params.containsKey("MayLookAt")) {
            controllerMayLookAt = true;
        }

        if (params.containsKey("IgnoreEffectCost")) {
            String cost = params.get("IgnoreEffectCost");
            buildIgnorEffectAbility(stAb, cost, affectedPlayers, affectedCards);
        }

        // modify players
        for (final Player p : affectedPlayers) {

            // add keywords
            if (addKeywords != null) {
                for (int i = 0; i < keywordMultiplier; i++) {
                    p.addChangedKeywords(addKeywords, removeKeywords == null ? new String[0] : removeKeywords, se.getTimestamp());
                }
            }

            if (params.containsKey("SetMaxHandSize")) {
                String mhs = params.get("SetMaxHandSize");
                if (mhs.equals("Unlimited")) {
                    p.setUnlimitedHandSize(true);
                } else {
                    int max = AbilityUtils.calculateAmount(hostCard, mhs, stAb);
                    p.setMaxHandSize(max);
                }
            }

            if (params.containsKey("RaiseMaxHandSize")) {
                String rmhs = params.get("RaiseMaxHandSize");
                int rmax = AbilityUtils.calculateAmount(hostCard, rmhs, stAb);
                p.setMaxHandSize(p.getMaxHandSize() + rmax);
            }

            if (params.containsKey("ManaColorConversion")) {
                AbilityUtils.applyManaColorConversion(p, params);
            }
        }

        // start modifying the cards
        for (int i = 0; i < affectedCards.size(); i++) {
            final Card affectedCard = affectedCards.get(i);
            
            // Gain control
            if (params.containsKey("GainControl")) {
                affectedCard.addTempController(hostCard.getController(), hostCard.getTimestamp());
            }

            // Change color words
            if (changeColorWordsTo != null) {
                final byte color;
                if (changeColorWordsTo.equals("ChosenColor")) {
                    if (hostCard.hasChosenColor()) {
                        color = MagicColor.fromName(Iterables.getFirst(hostCard.getChosenColors(), null));
                    } else {
                        color = 0;
                    }
                } else {
                    color = MagicColor.fromName(changeColorWordsTo);
                }

                if (color != 0) {
                    final String colorName = MagicColor.toLongString(color);
                    affectedCard.addChangedTextColorWord("Any", colorName, se.getTimestamp());
                }
            }

            // set P/T
            if (params.containsKey("CharacteristicDefining")) {
                if (setPower != -1) {
                    affectedCard.setBasePower(setPower);
                }
                if (setToughness != -1) {
                    affectedCard.setBaseToughness(setToughness);
                }
            } else // non CharacteristicDefining
            if ((setPower != -1) || (setToughness != -1)) {
                if (setP.startsWith("AffectedX")) {
                    setPower = CardFactoryUtil.xCount(affectedCard, AbilityUtils.getSVar(stAb, setP));
                }
                if (setT.startsWith("AffectedX")) {
                    setToughness = CardFactoryUtil.xCount(affectedCard, AbilityUtils.getSVar(stAb, setT));
                }
                affectedCard.addNewPT(setPower, setToughness, hostCard.getTimestamp());
            }

            // add P/T bonus
            if (addP.startsWith("AffectedX")) {
                powerBonus = CardFactoryUtil.xCount(affectedCard, AbilityUtils.getSVar(stAb, addP));
                se.addXMapValue(affectedCard, powerBonus);
            }
            if (addT.startsWith("AffectedX")) {
                toughnessBonus = CardFactoryUtil.xCount(affectedCard, AbilityUtils.getSVar(stAb, addT));
                se.addXMapValue(affectedCard, toughnessBonus);
            }
            affectedCard.addSemiPermanentPowerBoost(powerBonus);
            affectedCard.addSemiPermanentToughnessBoost(toughnessBonus);

            // add keywords
            // TODO regular keywords currently don't try to use keyword multiplier
            // (Although nothing uses it at this time)
            if ((addKeywords != null) || (removeKeywords != null) || removeAllAbilities) {
                affectedCard.addChangedCardKeywords(addKeywords, removeKeywords, removeAllAbilities,
                        hostCard.getTimestamp());
            }

            // add HIDDEN keywords
            if (addHiddenKeywords != null) {
                for (final String k : addHiddenKeywords) {
                    for (int j = 0; j < keywordMultiplier; j++) {
                        affectedCard.addHiddenExtrinsicKeyword(k);
                    }
                }
            }

            // add SVars
            if (addSVars != null) {
                for (final String sVar : addSVars) {
                    String actualSVar = hostCard.getSVar(sVar);
                    String name = sVar;
                    if (actualSVar.startsWith("SVar:")) {
                        actualSVar = actualSVar.split("SVar:")[1];
                        name = actualSVar.split(":")[0];
                        actualSVar = actualSVar.split(":")[1];
                    }
                    affectedCard.setSVar(name, actualSVar);
                }
            }

            if (addFullAbs != null) {
                for (final SpellAbility ab : addFullAbs) {
                    affectedCard.addSpellAbility(ab);
                }
            }

            // add abilities
            if (addAbilities != null) {
                for (String abilty : addAbilities) {
                    if (abilty.contains("CardManaCost")) {
                        StringBuilder sb = new StringBuilder();
                        int generic = affectedCard.getManaCost().getGenericCost();
                        if (generic > 0) {
                            sb.append(generic);
                        }
                        for (ManaCostShard s : affectedCard.getManaCost()) {
                            ColorSet cs = ColorSet.fromMask(s.getColorMask());
                            if(cs.isColorless()) continue;
                            sb.append(' ');
                            sb.append(s);
                        }
                        abilty = abilty.replace("CardManaCost", sb.toString().trim());
                    } else if (abilty.contains("ConvertedManaCost")) {
                        final String costcmc = Integer.toString(affectedCard.getCMC());
                        abilty = abilty.replace("ConvertedManaCost", costcmc);
                    }
                    if (abilty.startsWith("AB") || abilty.startsWith("ST")) { // grant the ability
                        final SpellAbility sa = AbilityFactory.getAbility(abilty, affectedCard);
                        sa.setTemporary(true);
                        sa.setIntrinsic(false);
                        sa.setOriginalHost(hostCard);
                        affectedCard.addSpellAbility(sa);
                    }
                }
            }

            // add Replacement effects
            if (addReplacements != null) {
                for (String rep : addReplacements) {
                    final ReplacementEffect actualRep = ReplacementHandler.parseReplacement(rep, affectedCard, false);
                    actualRep.setIntrinsic(false);
                    affectedCard.addReplacementEffect(actualRep).setTemporary(true);;
                }
            }
            
            // add Types
            if ((addTypes != null) || (removeTypes != null)) {
                affectedCard.addChangedCardTypes(addTypes, removeTypes, removeSuperTypes, removeCardTypes,
                        removeSubTypes, removeCreatureTypes, hostCard.getTimestamp());
            }

            // add colors
            if (addColors != null) {
                final long t = affectedCard.addColor(addColors, !se.isOverwriteColors(), true);
                se.addTimestamp(affectedCard, t);
            }

            // add triggers
            if (addTriggers != null) {
                for (final String trigger : addTriggers) {
                    final Trigger actualTrigger = TriggerHandler.parseTrigger(trigger, affectedCard, false);
                    actualTrigger.setIntrinsic(false);
                    affectedCard.addTrigger(actualTrigger).setTemporary(true);
                }
            }

            // add static abilities
            if (addStatics != null) {
                for (String s : addStatics) {
                    if (s.contains("ConvertedManaCost")) {
                        final String costcmc = Integer.toString(affectedCard.getCMC());
                        s = s.replace("ConvertedManaCost", costcmc);
                    }

                    StaticAbility stat = affectedCard.addStaticAbility(s);
                    stat.setTemporary(true);
                    stat.setIntrinsic(false);
                }
            }

            // remove triggers
            if (params.containsKey("RemoveTriggers") || removeAllAbilities) {
                for (final Trigger trigger : affectedCard.getTriggers()) {
                    trigger.setTemporarilySuppressed(true);
                }
            }

            // remove activated and static abilities
            if (removeAllAbilities) {
                for (final SpellAbility ab : affectedCard.getSpellAbilities()) {
                    ab.setTemporarilySuppressed(true);
                }
                for (final StaticAbility stA : affectedCard.getStaticAbilities()) {
                    stA.setTemporarilySuppressed(true);
                }
                for (final ReplacementEffect rE : affectedCard.getReplacementEffects()) {
                    rE.setTemporarilySuppressed(true);
                }
            }

            if (controllerMayLookAt) {
                affectedCard.setMayLookAt(controller, true);
            }
        }
        
        return affectedCards;
    }

    private static void buildIgnorEffectAbility(final StaticAbility stAb, final String costString, final List<Player> players, final List<Card> cards) {
        final List<Player> validActivator = new ArrayList<Player>(players);
        for (final Card c : cards) {
            validActivator.add(c.getController());
        }
        final Card sourceCard = stAb.getHostCard();
        Cost cost = new Cost(costString, true);
        final AbilityStatic addIgnore = new AbilityStatic(sourceCard, cost, null) {

            @Override
            public void resolve() {
                stAb.addIgnoreEffectPlayers(this.getActivatingPlayer());
                stAb.setIgnoreEffectCards(cards);
            }

            @Override
            public boolean canPlay() {
                return validActivator.contains(this.getActivatingPlayer())
                        && sourceCard.isInPlay();
            }

        };
        addIgnore.setTemporary(true);
        addIgnore.setIntrinsic(false);
        addIgnore.setApi(ApiType.InternalIgnoreEffect);
        addIgnore.setDescription(cost + " Ignore the effect until end of turn.");
        sourceCard.addSpellAbility(addIgnore);

        final GameCommand removeIgnore = new GameCommand() {
            private static final long serialVersionUID = -5415775215053216360L;
            @Override
            public void run() {
                stAb.clearIgnoreEffects();
            }
        };
        sourceCard.getGame().getEndOfTurn().addUntil(removeIgnore);
        sourceCard.addLeavesPlayCommand(removeIgnore);
    }

    private static ArrayList<Player> getAffectedPlayers(final StaticAbility stAb) {
        final Map<String, String> params = stAb.getMapParams();
        final Card hostCard = stAb.getHostCard();
        final Player controller = hostCard.getController();

        final ArrayList<Player> players = new ArrayList<Player>();

        if (!params.containsKey("Affected")) {
            return players;
        }

        final String[] strngs = params.get("Affected").split(",");

        for (Player p : controller.getGame().getPlayersInTurnOrder()) {
            if (p.isValid(strngs, controller, hostCard)) {
                players.add(p);
            }
        }
        players.removeAll(stAb.getIgnoreEffectPlayers());

        return players;
    }

    private static List<Card> getAffectedCards(final StaticAbility stAb) {
        final Map<String, String> params = stAb.getMapParams();
        final Card hostCard = stAb.getHostCard();
        final Game game = hostCard.getGame();
        final Player controller = hostCard.getController();

        if (params.containsKey("CharacteristicDefining")) {
            return Lists.newArrayList(hostCard); // will always be the card itself
        }

        // non - CharacteristicDefining
        CardCollection affectedCards;

        if (params.containsKey("AffectedZone")) {
            affectedCards = new CardCollection(game.getCardsIn(ZoneType.listValueOf(params.get("AffectedZone"))));
        } else {
            affectedCards = new CardCollection(game.getCardsIn(ZoneType.Battlefield));
        }

        if (params.containsKey("Affected") && !params.get("Affected").contains(",")) {
            if (params.get("Affected").contains("Self")) {
                affectedCards = new CardCollection(hostCard);
            } else if (params.get("Affected").contains("EnchantedBy")) {
                affectedCards = new CardCollection(hostCard.getEnchantingCard());
            } else if (params.get("Affected").contains("EquippedBy")) {
                affectedCards = new CardCollection(hostCard.getEquipping());
            } else if (params.get("Affected").equals("EffectSource")) {
                affectedCards = new CardCollection(AbilityUtils.getDefinedCards(hostCard, params.get("Affected"), null));
                return affectedCards;
            }
        }

        if (params.containsKey("Affected")) {
            affectedCards = CardLists.getValidCards(affectedCards, params.get("Affected").split(","), controller, hostCard);
        }
        affectedCards.removeAll((List<?>)stAb.getIgnoreEffectCards());
        return affectedCards;
    }
}

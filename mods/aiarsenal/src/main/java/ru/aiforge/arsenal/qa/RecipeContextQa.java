package ru.aiforge.arsenal.qa;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

/** Actual loaded RecipeManager matches/assemble/remaining-items checks. This is
 * recipe-context coverage for passive parts, NOT firing/using the crafted output
 * and NOT a claim that a player completed the crafting GUI. */
final class RecipeContextQa {
    private RecipeContextQa() {}
    static JsonObject run(ServerPlayer p) throws java.io.IOException {
        JsonArray rows=new JsonArray();Set<String> consumed=new TreeSet<>();int failed=0;
        for(CraftingRecipe recipe:p.serverLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if(!recipe.getId().getNamespace().equals("aiarsenal"))continue;
            if(!(recipe instanceof ShapedRecipe) && !(recipe instanceof ShapelessRecipe))continue;
            List<Ingredient> ingredients=recipe.getIngredients();
            int width=recipe instanceof ShapedRecipe shaped?shaped.getWidth():3;
            List<ItemStack> base=new ArrayList<>();for(var ingredient:ingredients) {
                var choices=ingredient.getItems();base.add(choices.length==0?ItemStack.EMPTY:choices[0].copy());
            }
            // Base recipe, then every mod ingredient/tag alternative in its actual slot.
            List<List<ItemStack>> cases=new ArrayList<>();cases.add(base);
            for(int slot=0;slot<ingredients.size();slot++)for(var choice:ingredients.get(slot).getItems()) {
                if(!BuiltInRegistries.ITEM.getKey(choice.getItem()).getNamespace().equals("aiarsenal"))continue;
                var variant=new ArrayList<>(base);variant.set(slot,choice.copy());cases.add(variant);
            }
            for(var input:cases) {
                JsonObject row=new JsonObject();row.addProperty("recipe",recipe.getId().toString());
                try {
                    var menu=new AbstractContainerMenu(null,-1) {
                        @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
                        @Override public boolean stillValid(Player player){return true;}
                    };
                    var grid=new TransientCraftingContainer(menu,3,3);
                    JsonArray ids=new JsonArray();
                    for(int n=0;n<input.size();n++) {
                        ItemStack stack=input.get(n).copy();grid.setItem((n/width)*3+n%width,stack);
                        if(!stack.isEmpty())ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    }
                    row.add("inputs",ids);
                    if(!recipe.matches(grid,p.serverLevel()))throw new IllegalStateException("loaded recipe does not match its ingredient grid");
                    var output=recipe.assemble(grid,p.serverLevel().registryAccess());
                    var declared=recipe.getResultItem(p.serverLevel().registryAccess());
                    if(output.isEmpty() || output.getCount()<=0 || !ItemStack.isSameItemSameTags(output,declared) || output.getCount()!=declared.getCount())
                        throw new IllegalStateException("assemble differs from declared result");
                    if(recipe.getRemainingItems(grid).size()!=grid.getContainerSize())throw new IllegalStateException("wrong remaining-item grid size");
                    row.addProperty("output",BuiltInRegistries.ITEM.getKey(output.getItem()).toString());row.addProperty("count",output.getCount());
                    row.addProperty("status","PASS");for(var id:ids)if(id.getAsString().startsWith("aiarsenal:"))consumed.add(id.getAsString());
                } catch(Exception error) {failed++;row.addProperty("status","FAIL");row.addProperty("error",error.toString());}
                rows.add(row);
            }
        }
        JsonObject report=new JsonObject();report.addProperty("scope","loaded crafting recipes: every mod ingredient alternative, matches/assemble/remainders; no crafted-output use claim");
        report.addProperty("status",failed==0 && rows.size()>0?"PASS":"FAIL");report.addProperty("failed",failed);report.add("cases",rows);
        report.add("ingredientItems",new Gson().toJsonTree(consumed));
        Files.createDirectories(Path.of("logs"));Files.writeString(Path.of("logs/context-recipes.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        JsonObject summary=new JsonObject();summary.addProperty("status",report.get("status").getAsString());summary.addProperty("cases",rows.size());summary.addProperty("ingredientItems",consumed.size());return summary;
    }
}

// Minecraft 1.20.1 / Forge 47. Copy the input before datafixing mutates __context.
// Match the unique descriptor instead of hardcoding an obfuscated method name.
function initializeCoreMod() {
    var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
    var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
    var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
    var Opcodes = Java.type('org.objectweb.asm.Opcodes');
    var descriptor = '(Lnet/minecraft/resources/ResourceKey;Ljava/util/function/Supplier;Lnet/minecraft/nbt/CompoundTag;Ljava/util/Optional;)Lnet/minecraft/nbt/CompoundTag;';
    return {
        'chunk_upgrade_owns_its_tag': {
            'target': {'type': 'CLASS', 'name': 'net.minecraft.world.level.chunk.storage.ChunkStorage'},
            'transformer': function(clazz) {
                var matched = 0;
                for (var i = 0; i < clazz.methods.size(); i++) {
                    var method = clazz.methods.get(i);
                    if (String(method.desc) !== descriptor || (method.access & Opcodes.ACC_STATIC) !== 0) continue;
                    var code = new InsnList();
                    code.add(new VarInsnNode(Opcodes.ALOAD, 3));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        'ru/aiforge/arsenal/stability/ChunkTagSnapshot', 'forUpgrade',
                        '(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;', false));
                    code.add(new VarInsnNode(Opcodes.ASTORE, 3));
                    method.instructions.insert(code);
                    matched++;
                }
                if (matched !== 1) throw new Error('AI Arsenal chunk snapshot: expected exactly one upgrade method, found ' + matched);
                return clazz;
            }
        }
    };
}

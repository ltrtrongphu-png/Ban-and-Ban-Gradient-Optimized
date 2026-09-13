package com.example.bab.verification;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Generator tao chunk hoan toan trong rong - dung cho the gioi "limbo" xac minh.
 * Khong sinh dia hinh, khong sinh quai, giup the gioi nhe va an toan (khong co gi
 * de nguoi choi tuong tac/pha hoai trong luc cho xac minh).
 *
 * FIX QUAN TRONG: ban goc chi override method generateChunkData(World, ...) da bi
 * DEPRECATED. Tren cac phien ban Bukkit/Paper hien dai, viec sinh chunk da duoc
 * tach thanh 4 buoc rieng (generateBedrock / generateNoise / generateSurface /
 * generateCaves), moi buoc nhan WorldInfo thay vi World. Neu chi override method
 * cu, tuy build server ma method do co the KHONG duoc goi, khien chunk sinh ra
 * theo mac dinh (co dia hinh that) thay vi rong hoan toan. Dieu nay khong lam chet
 * nguoi choi truc tiep, nhung la nguyen nhan tiem an gay sai lech giua "the gioi
 * ma plugin nghi la rong" va "the gioi thuc te tren dia", gop phan vao cac loi
 * NoClip/NoFall khi chunk phai sinh/nap lai. Sua bang cach override ca 2 bo
 * method (cu + moi) de dam bao void tren MOI phien ban server.
 */
public class VoidGenerator extends ChunkGenerator {

    // ===== Bo method MOI (khuyen nghi, duoc goi tren cac ban Bukkit/Paper hien dai) =====

    @Override
    public void generateBedrock(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // Khong lam gi - khong sinh bedrock
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // Khong lam gi - khong sinh dia hinh/noise
    }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // Khong lam gi - khong sinh be mat (co/dat/da...)
    }

    @Override
    public void generateCaves(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // Khong lam gi - khong sinh hang dong
    }

    // ===== Bo method CU (deprecated) - giu lai de tuong thich nguoc voi cac fork/ban cu =====

    @SuppressWarnings("deprecation")
    @Override
    public ChunkGenerator.ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
        return createChunkData(world);
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.*;
import java.util.*;


public class cachesim{
    public static class Block {
    
        private int valid;
        private int tag;
        private String[] data;
        private int dirty;
        private int set;

        public int getValid(){
            return valid;
        }
        public int getTag(){
            return tag;
        }
        public String[] getData(){
            return data;
        }
        public int getDirty(){
            return dirty;
        }
        public int getSet(){
            return set;
        }
    }
    public static Block makeNewBlock(int valid, int tag, int blockSize, int dirty, int set){
        Block ret = new Block();
        ret.valid = valid;
        ret.tag = tag; 
        ret.data = new String[blockSize];
        ret.dirty = dirty;
        ret.set = set;
        return ret;
    }

    public static String[] makeMemory(int size){
      String[] ret = new String[size];
      Arrays.fill(ret, "00");
      return ret;
    }

    public static ArrayList<Queue<Block>> makeCache(int cacheSize, int ways, int blockSize, String write, int sets) {
        ArrayList<Queue<Block>> ret = new ArrayList<>();
        for(int i = 0; i < sets; i ++){
            Queue<Block> temp = new LinkedList<>(); // put new queue in the cache
            for(int j = 0; j < ways; j ++){
                if(write.equals("wt")){
                    Block notDirty = makeNewBlock(0, -1, blockSize, -1, i);
                    temp.add(notDirty);
                }
                else if(write.equals("wb")){
                    Block Dirty = makeNewBlock(0, -1, blockSize, 0, i);
                    temp.add(Dirty);
                } 
            }
            ret.add(temp);
        }
        return ret;
    }

    public static String[] getValueArr (String value){
        ArrayList<String> ret = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int byTwo = 0;
        for(int i = 0; i < value.length(); i ++){
            if(byTwo < 2){
                sb.append(value.charAt(i));
                byTwo ++;
            }
            if(byTwo == 2){
                ret.add(sb.toString());
                sb = new StringBuilder();
                byTwo = 0;
            }
        }
        String[] arr = ret.toArray(new String[ret.size()]);
        return arr;
    }

    public static String storeWB (ArrayList<Queue<Block>> cache, int address, int accessSize, String value, int blockSize, int sets, String[] memory, String hexAddress){
        int set = (address/blockSize) % sets; // gets the set from the address.
        int tag = address/(sets*blockSize);
        int blockOffset = address % blockSize;
        int found = 0;
        int empty = 0;
        String[] valuearr = getValueArr(value);

        // if hit, write to cache but not to memory and set dirty bit to 1
        for(Block blocks : cache.get(set)){
            if(blocks.getTag() == tag && blocks.getValid() == 1){
                found = 1;
                blocks.dirty = 1;
                for(int i = 0; i < accessSize; i ++){
                    blocks.data[i+blockOffset] = valuearr[i];
                }
                cache.get(set).remove(blocks);
                cache.get(set).add(blocks);
                break;
            }
        }

        // if miss, load block from memory, evicting a block if necessary
        if(found == 0){
            int memAddress = getAddress(set, tag, blockSize, sets);
            String loadFromMem[] = Arrays.copyOfRange(memory, memAddress, memAddress+blockSize);
            for(Block blocks: cache.get(set)){
                if(blocks.getValid() == 0){
                    empty = 1;
                    // load in the memory into the block
                    for(int i = 0; i < blockSize; i ++){
                        blocks.data[i] = loadFromMem[i];
                    }
                    // write the data to the block
                    for(int j = 0; j < accessSize; j ++){
                        blocks.data[j+blockOffset] = valuearr[j];
                    }
                    blocks.tag = tag;
                    blocks.set = set;
                    blocks.valid = 1;
                    blocks.dirty = 1;

                    cache.get(set).remove(blocks);
                    cache.get(set).add(blocks);
                    break;
                }
            }
        }
        // if we need to evict
        if(found == 0 && empty == 0){ 
            int memAddress = getAddress(set, tag, blockSize, sets);
            String loadFromMem[] = Arrays.copyOfRange(memory, memAddress, memAddress+blockSize);

            Block insertBlock = makeNewBlock(1, tag, blockSize, 1, set);
            for(int i = 0; i < blockSize; i ++){
                insertBlock.data[i] = loadFromMem[i];
            }
            for(int j = 0; j < accessSize; j ++){
                insertBlock.data[j+blockOffset] = valuearr[j];
            }
            Block removed = cache.get(set).remove(); // remove the lru block from the queue
            if(removed.getDirty() == 1){
                updateMemoryWB(memory, removed, blockSize, sets);
            }
            cache.get(set).add(insertBlock);
        }

        if(found == 0){
          return "store " + hexAddress + " " + "miss";
        }
        return "store " + hexAddress + " " + "hit";
    }

    public static int getAddress(int set, int tag, int blockSize, int sets){
        String newTag = Integer.toBinaryString(tag);
        String newSet = Integer.toBinaryString(set);
        int numOffSet = log2(blockSize);
        int numSet = log2(sets);
        int numTag = 16-numSet-numOffSet;
      
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < numTag-newTag.length(); i ++){
          sb.append("0");
        }
        sb.append(newTag);
        for(int i = 0; i < numSet-newSet.length(); i ++){
          sb.append("0");
        }
        sb.append(newSet);
        for(int i = 0; i < numOffSet; i ++){
            sb.append("0");
        }
        String buffer = sb.toString();
        int address = Integer.parseInt(buffer, 2);
        return address;
    }

    public static void updateMemoryWB(String[] memory, Block removed, int blockSize, int sets){
        int address = getAddress(removed.getSet(), removed.getTag(), blockSize, sets);
        String[] dataArr = removed.getData();

        for(int i = 0; i < blockSize; i ++){
            memory[i+address] = dataArr[i];
        }
    }

    public static int log2(int N){
        int result = (int)(Math.log(N) / Math.log(2));
        return result;
    }
 
    public static String storeWT (ArrayList<Queue<Block>> cache, int address, int accessSize, String value, int blockSize, int sets, String[] memory, String hexAddress){
        int set = (address/blockSize) % sets;
        int tag = address/(sets*blockSize);
        int blockOffset = address % blockSize;
        int found = 0;
        String[] valuearr = getValueArr(value);

        // write to memory
        for(int i = 0; i < accessSize; i ++){
            memory[i+address] = valuearr[i];
        }
    
        // if block exists, write to cache
        for(Block blocks : cache.get(set)){
            if((blocks.getTag() == tag) && (blocks.getValid() == 1)){
                found = 1;
                for(int i = 0; i < accessSize; i ++){
                    blocks.data[i+blockOffset] = valuearr[i]; // we want the first index in the value array
                }
                cache.get(set).remove(blocks);
                cache.get(set).add(blocks);
                break;
            }   
        }
    
        if(found == 0){
            return "store " + hexAddress + " " + "miss";
        }
        return "store " + hexAddress + " " + "hit";
    }

    public static String loadWT (ArrayList<Queue<Block>> cache, int address, int accessSize, int blockSize, int sets, String[] memory, String hexAddress){
        int set = (address/blockSize) % sets;
        int tag = address/(sets*blockSize);
        int blockOffset = address % blockSize;
        int found = 0;
        int empty = 0;
        StringBuilder sb = new StringBuilder();

        // load from memory
        int memAddress = getAddress(set, tag, blockSize, sets);
        String loadFromMem[] = Arrays.copyOfRange(memory, memAddress, memAddress+blockSize);

        // if it's a hit, then we read data from the cache
        for(Block blocks: cache.get(set)){
            if((blocks.getTag() == tag) && (blocks.getValid() == 1)){ 
                found = 1;
                for(int i = blockOffset; i < (blockOffset + accessSize); i ++){
                    sb.append(blocks.getData()[i]);
                }
                cache.get(set).remove(blocks);
                cache.get(set).add(blocks);
                break;
            }
        }

        if(found == 0){
            for(Block blocks: cache.get(set)){
                if(blocks.getValid() == 0){
                    empty = 1;
                    for(int i = 0; i < blockSize; i ++){
                        blocks.data[i] = loadFromMem[i];
                    }
                    for(int i = blockOffset; i < (blockOffset + accessSize); i ++){ 
                        sb.append(blocks.getData()[i]);
                    }
                    blocks.tag = tag;
                    blocks.set = set;
                    blocks.valid = 1;
                    blocks.dirty = -1;

                    cache.get(set).remove(blocks);
                    cache.get(set).add(blocks);
                    break;
                }
            }
        }

        // if miss, load the block from memory and read it, evicting a block if necessary
        if(found == 0 && empty == 0){
            Block insertBlock = makeNewBlock(1, tag, blockSize, -1, set);
            for(int i = 0; i < blockSize; i ++){
                insertBlock.data[i] = loadFromMem[i];
            }
            for(int i = blockOffset; i < (blockOffset + accessSize); i ++){ 
                sb.append(insertBlock.getData()[i]);
            }
            cache.get(set).remove(); // remove the lru block from the queue
            cache.get(set).add(insertBlock);
        }

        if(found == 0){
            return "load " + hexAddress + " miss " + sb;
        }
        else{
            return "load " + hexAddress + " hit " + sb;
        }
    }

    public static String loadWB (ArrayList<Queue<Block>> cache, int address, int accessSize, int blockSize, int sets, String[] memory, String hexAddress){
        
        int set = (address/blockSize) % sets;
        int tag = address/(sets*blockSize);
        int blockOffset = address % blockSize;
        int found = 0;
        StringBuilder sb = new StringBuilder();

        for(Block blocks: cache.get(set)){
            if((blocks.getTag()) == tag && blocks.getValid() == 1){ // if it's a hit, then we read data from the cache
                found = 1;
                for(int i = blockOffset; i < (blockOffset + accessSize); i ++){ 
                    sb.append(blocks.data[i]);
                }
                cache.get(set).remove(blocks);
                cache.get(set).add(blocks);
                break;
            }
        }
        // if miss, load the block from memory and read it, evicting a block if necessary. Set dirty bit to load block to 0
        if(found == 0){
            int memAddress = getAddress(set, tag, blockSize, sets);
            String loadFromMem[] = Arrays.copyOfRange(memory, memAddress, memAddress+blockSize);
            Block insertBlock = makeNewBlock(1, tag, blockSize, 0, set);
            for(int i = 0; i < blockSize; i ++){
                insertBlock.data[i] = loadFromMem[i];
            }
            for(int i = blockOffset; i < (blockOffset + accessSize); i ++){ 
                sb.append(insertBlock.data[i]);
            }
            Block removed = cache.get(set).remove(); // remove the lru block from the queue
            if(removed.getDirty() == 1){
                updateMemoryWB(memory, removed, blockSize, sets);
            }
            cache.get(set).add(insertBlock);
        }

        if(found == 0){
            return "load " + hexAddress + " miss " + sb;
        }
        else{
            return "load " + hexAddress + " hit " + sb;
        }
    }

    public static void main(String[] args){
        // get arguments
        int cacheSize = 1024 * (Integer.parseInt(args[1]));
        int associativity = Integer.parseInt(args[2]);
        String writeType = args[3];
        int blockSize = Integer.parseInt(args[4]);
        int numFrames = cacheSize / blockSize;
        int sets = numFrames / associativity;
        ArrayList<Queue<Block>> cache = makeCache(cacheSize, associativity, blockSize, writeType, sets);
        String[] memory = makeMemory(65536);

        BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(args[0]));
			String currentLine = reader.readLine();

            // if writeType is write-through
            if(writeType.equals("wt")){
                while(currentLine != null){
                    String line = currentLine; 
                    String[] arr = line.split(" "); 
                    if(arr[0].equals("load")){
                        System.out.println(loadWT(cache, Integer.parseInt(arr[1], 16), Integer.parseInt(arr[2]), blockSize, sets, memory, arr[1]));
                    }
                    else if(arr[0].equals("store")){
                        System.out.println(storeWT(cache, Integer.parseInt(arr[1], 16), Integer.parseInt(arr[2]), arr[3], blockSize, sets, memory, arr[1]));
                    }
                    currentLine = reader.readLine();
                }
            }

            else if(writeType.equals("wb")){
                while(currentLine != null){
                    String line = currentLine; 
                    String[] arr = line.split(" ");
                    if(arr[0].equals("load")){
                        System.out.println(loadWB(cache, Integer.parseInt(arr[1], 16), Integer.parseInt(arr[2]), blockSize, sets, memory, arr[1]));
                    }
                    else if(arr[0].equals("store")){
                        System.out.println(storeWB(cache, Integer.parseInt(arr[1], 16), Integer.parseInt(arr[2]), arr[3], blockSize, sets, memory, arr[1]));
                    }
                    currentLine = reader.readLine();
                }
            }
            reader.close();
        } 
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}


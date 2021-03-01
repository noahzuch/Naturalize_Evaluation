package renaming.evaluation;

import codemining.lm.ngram.LongTrie;
import codemining.lm.ngram.Trie;
import renaming.ngram.IdentifierNeighborsNGramLM;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class CompareTries {

    public static void compareTries(IdentifierNeighborsNGramLM modelOrig, IdentifierNeighborsNGramLM modelNew){
        LongTrie<String> origTrie = modelOrig.getTrie();
        LongTrie<String> newTrie = modelNew.getTrie();
        compareSets(origTrie.alphabet.keySet(),newTrie.alphabet.keySet());
        compareTrieNode(origTrie.getRoot(),newTrie.getRoot());
    }

    private static void compareTrieNode(Trie.TrieNode<Long> node1, Trie.TrieNode<Long> node2){
        if(node1.count != node2.count || node1.terminateHere != node2.terminateHere){
            System.out.println("Count term is different");
        }else{
            compareSets(node1.prods.keySet(),node2.prods.keySet());
            for (Long prod : node1.prods.keySet()) {
                compareTrieNode(node1.prods.get(prod),node2.prods.get(prod));
            }
        }
    }

    private static <E> void compareSets(Set<E> set1, Set<E> set2){
        Set<E> set1C = new HashSet<>(set1);
        Set<E> set2C = new HashSet<>(set2);
        set1C.removeAll(set2);
        set2C.removeAll(set1);
        if(!set1C.isEmpty() || !set2C.isEmpty()){
            System.out.println("Alphabet not equal");
        }
    }

    public static <E> Stream<Trie.TrieNode<E>> streamNodes(Trie.TrieNode<E> root){
        return Stream.concat(Stream.of(root),root.prods.values().stream().flatMap(CompareTries::streamNodes));
    }
}

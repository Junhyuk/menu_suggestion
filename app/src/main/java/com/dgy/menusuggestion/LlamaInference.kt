package com.dgy.menusuggestion

class LlamaInference {
    companion object {
        init {
            System.loadLibrary("menusuggestion")
        }
    }

    external fun loadModel(modelPath: String): Boolean
    external fun generate(prompt: String): String
    external fun unloadModel()
}


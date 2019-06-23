<template>
    <iframe v-bind:src="opsUrl"></iframe>
</template>

<script>
    export default {
        data() {
            return {opsUrl: this.Servers.serverList.opsServer}
        },
        methods: {}
    }
</script>
<style>
    iframe {
        width: 85vw;
        height: 50vw;
    }
</style>

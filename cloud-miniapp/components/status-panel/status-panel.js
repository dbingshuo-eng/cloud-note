Component({
  properties: {
    mode: {
      type: String,
      value: 'empty'
    },
    message: {
      type: String,
      value: ''
    },
    actionText: {
      type: String,
      value: ''
    }
  },

  methods: {
    onAction() {
      this.triggerEvent('action');
    }
  }
});
